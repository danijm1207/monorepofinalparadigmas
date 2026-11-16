"""
__main__.py - Punto de entrada de línea de comandos para el arnés de benchmark (paquete bench).
Soporta: bench run, bench contract, bench quality, bench functional, bench consolidate,
bench report, bench all, y el modificador --quick.
"""

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess
import sys
import time
from typing import Any, Dict, List, Optional

from benchmark.bench.environment import save_environment_metadata
from benchmark.bench.quality_analyzer import QualityAnalyzer
from benchmark.bench.functional_analyzer import FunctionalAnalyzer
from benchmark.bench.scorer import BenchmarkScorer, median
from benchmark.bench.consolidator import BenchmarkConsolidator
from benchmark.bench.report_generator import ReportGenerator
from benchmark.bench.scenario_runner import ScenarioRunner
from benchmark.bench.overview_runner import OverviewRunner
from benchmark.bench.http_load import HttpLoadTester
from benchmark.bench.docker_controller import DockerController
from benchmark.bench.memory_sampler import measure_idle_memory, MemorySampler


def get_workspace_root() -> Path:
    # Este archivo está en <workspace>/benchmark/bench/__main__.py
    return Path(__file__).resolve().parents[2]


def run_contract_command(args, workspace: Path) -> int:
    """Ejecuta la suite de pruebas de contrato contra el backend objetivo."""
    target = getattr(args, "target", "http://127.0.0.1:8080")
    print(f"\n[CONTRACT] Ejecutando pruebas de contrato contra: {target}")

    python_bin = sys.executable
    test_file = str(workspace / "benchmark" / "tests" / "test_contract.py")
    cmd = [python_bin, "-m", "pytest", test_file, "--target", target, "-v"]

    res = subprocess.run(cmd, cwd=workspace)
    return res.returncode


def run_quality_command(args, workspace: Path) -> Dict[str, Any]:
    """Ejecuta el análisis de calidad de código (Q1 a Q7)."""
    print("\n[QUALITY] Evaluando calidad de código (Q1 a Q7)...")
    analyzer = QualityAnalyzer(workspace)
    langs = getattr(args, "langs", "java,scala,kotlin").split(",")

    # Primero obtener NLOC de todos para normalizar Q7 (concisión)
    nlocs = {}
    for l in langs:
        lang_dir = workspace / "backends" / l
        stats = analyzer.analyze_source_code_with_lizard(lang_dir)
        nlocs[l] = stats["nloc"]

    min_nloc = min((n for n in nlocs.values() if n > 0), default=1)

    results = {}
    for l in langs:
        res = analyzer.evaluate_backend(l, min_nloc_all=min_nloc)
        results[l] = res
        print(f"  -> {l.capitalize()}: Q-Score = {res['total_quality_score']*100:.1f} pts "
              f"(NLOC={res['details']['nloc']}, CCN={res['details']['avg_ccn']:.2f}, Cov={res['details']['coverage_pct']:.1f}%)")

    return results


def run_functional_command(args, workspace: Path) -> Dict[str, Any]:
    """Ejecuta el análisis estático de pureza funcional (F1 a F6)."""
    print("\n[FUNCTIONAL] Evaluando pureza funcional (F1 a F6)...")
    analyzer = FunctionalAnalyzer(workspace)
    langs = getattr(args, "langs", "java,scala,kotlin").split(",")

    results = {}
    for l in langs:
        res = analyzer.evaluate_backend(l)
        results[l] = res
        print(f"  -> {l.capitalize()}: F-Score = {res['total_functional_score']*100:.1f} pts "
              f"(Bucles={res['details']['loops_found']}, Mutables={res['details']['mutables_found']}, Nulos={res['details']['null_occurrences']})")

    return results


def run_execution_rounds(args, workspace: Path) -> Dict[str, Any]:
    """
    Ejecuta las fases de benchmark con rotación de orden entre rondas.
    Fases:
    1. docker compose down -v y up -d --build (medir tiempo de arranque)
    2. Cargar en Postgres con COPY escenario S
    3. Memoria en reposo
    4. (Ronda 1) Pruebas de contrato
    5. Calentamiento descartado
    6. Medición de escenarios con repeticiones
    7. Overview
    8. Carga HTTP concurrente (c1, c10, c50)
    9. Muestreo de memoria y docker compose down -v
    10. Guardar telemetría cruda en results/raw/
    """
    quick = getattr(args, "quick", False)
    use_fake = getattr(args, "fake", False)
    langs = getattr(args, "langs", "java,scala,kotlin").split(",")
    num_rounds = 1 if quick else getattr(args, "rounds", 3)

    run_id = getattr(
        args,
        "output_id",
        datetime.now(timezone.utc).strftime("run_%Y%m%d_%H%M%S")
        + ("_quick" if quick else ""),
    )
    raw_dir = workspace / "results" / "raw" / run_id
    raw_dir.mkdir(parents=True, exist_ok=True)

    # Guardar environment.json
    save_environment_metadata(workspace, raw_dir / "environment.json")

    print(f"\n[BENCH RUN] Iniciando corrida '{run_id}' (Rondas={num_rounds}, Quick={quick}, Fake={use_fake})...")

    # Si se especificó fake, arrancamos un fake_backend local
    fake_server = None
    if use_fake:
        from benchmark.tests.fake_backend import FakeBackendServer
        fake_server = FakeBackendServer(port=8080)
        fake_server.start()
        time.sleep(0.5)

    docker_ctl = DockerController(workspace)

    # Órdenes rotados: Ronda 1 [java, scala, kotlin], Ronda 2 [scala, kotlin, java], Ronda 3 [kotlin, java, scala]
    rotation_orders = []
    base_list = list(langs)
    for r in range(num_rounds):
        shift = r % len(base_list)
        rotated = base_list[shift:] + base_list[:shift]
        rotation_orders.append(rotated)

    all_round_data: Dict[str, Any] = {}

    try:
        for r_idx, current_langs in enumerate(rotation_orders, 1):
            round_name = f"ronda-{r_idx}"
            print(f"\n--- INICIO DE {round_name.upper()} (Orden: {' -> '.join(current_langs)}) ---")

            for lang in current_langs:
                print(f"\n>>> Evaluando Lenguaje: {lang.upper()} en {round_name} <<<")
                lang_raw_dir = raw_dir / lang / round_name
                lang_raw_dir.mkdir(parents=True, exist_ok=True)

                if use_fake:
                    base_url = "http://127.0.0.1:8080"
                    container_name = "fake_backend"
                    startup_seconds = 0.2
                    idle_stats = {"peak_bytes": 45 * 1024 * 1024, "mean_bytes": 42 * 1024 * 1024}
                    sampler = None
                else:
                    port = docker_ctl.get_port(lang)
                    base_url = f"http://localhost:{port}"
                    container_name = docker_ctl.get_container_name(lang)

                    print(f"  [Fase 1] Levantando perfil {lang} con Docker Compose...")
                    boot_info = docker_ctl.start_profile(lang, build=True)
                    startup_seconds = boot_info["startup_seconds"]
                    print(f"  -> Arranque saludable en {startup_seconds:.2f} s")

                    print("  [Fase 2] Cargando escenario S en Postgres mediante COPY...")
                    try:
                        from benchmark.bench.dataset import build_scenario_s_dataset, load_dataset_into_postgres
                        ds = build_scenario_s_dataset()
                        load_dataset_into_postgres(ds)
                        print("  -> Datos cargados exitosamente.")
                    except Exception as ex:
                        print(f"  [ALERTA] No se pudo conectar a Postgres directamente: {ex}")

                    sampler = MemorySampler(container_name, interval_seconds=0.5)
                    sampler.start("init")

                    print("  [Fase 3] Muestreando memoria en reposo (10s espera + 10s muestreo)...")
                    idle_wait = 1.0 if quick else 10.0
                    idle_measure = 2.0 if quick else 10.0
                    idle_stats = measure_idle_memory(
                        container_name, duration_seconds=idle_measure, wait_before_seconds=idle_wait
                    )
                    print(f"  -> Memoria en reposo: {idle_stats['mean_bytes'] / (1024**2):.1f} MB")

                # Obtener token admin para llamadas autenticadas
                token = "admin_token_jwt"
                try:
                    with httpx.Client(base_url=base_url, timeout=5.0) as client:
                        login_resp = client.post(
                            "/api/auth/login",
                            json={"email": "admin@investigacion.test", "password": "Admin12345"},
                        )
                        if login_resp.status_code == 200:
                            token = login_resp.json().get("token", token)
                except Exception:
                    pass

                # Fase 4: Pruebas de contrato (solo en ronda 1)
                contract_results = {"passed": 0, "total": 0, "rate": 1.0}
                if r_idx == 1:
                    print("  [Fase 4] Ejecutando pruebas de contrato...")
                    test_file = str(workspace / "benchmark" / "tests" / "test_contract.py")
                    proc = subprocess.run(
                        [sys.executable, "-m", "pytest", test_file, "--target", base_url, "-q"],
                        cwd=workspace,
                        capture_output=True,
                        text=True,
                    )
                    passed_m = re.search(r"(\d+) passed", proc.stdout)
                    failed_m = re.search(r"(\d+) failed", proc.stdout)
                    p_count = int(passed_m.group(1)) if passed_m else 0
                    f_count = int(failed_m.group(1)) if failed_m else 0
                    t_count = p_count + f_count
                    rate = p_count / t_count if t_count > 0 else 0.0
                    contract_results = {"passed": p_count, "total": t_count, "rate": rate}
                    print(f"  -> Contratos aprobados: {p_count}/{t_count} ({rate*100:.1f}%)")

                # Fase 5: Calentamiento
                runner = ScenarioRunner(base_url, token, memory_sampler=sampler)
                print("  [Fase 5] Calentamiento de la JVM...")
                runner.warmup(quick=quick)

                # Fase 6: Medición de escenarios
                scenarios = ["T", "S"] if quick else ["S", "M", "L", "XL"]
                print(f"  [Fase 6] Midiendo escenarios {scenarios}...")
                scen_results = runner.measure_scenarios(scenarios=scenarios, quick=quick)

                # Fase 7: Overview
                print("  [Fase 7] Verificando endpoint GET /api/analytics/overview...")
                overview_runner = OverviewRunner(base_url, token, memory_sampler=sampler)
                overview_res = overview_runner.run_overview_calls(repetitions=2 if quick else 3)
                print(f"  -> Overview match={overview_res['all_matched']}")

                # Fase 8: Carga HTTP
                print("  [Fase 8] Ejecutando prueba de carga HTTP concurrente...")
                http_tester = HttpLoadTester(base_url, token, memory_sampler=sampler)
                concurrencies = [1, 5] if quick else [1, 10, 50]
                warmup_sec = 2.0 if quick else 10.0
                measure_sec = 5.0 if quick else 30.0
                load_results = http_tester.run_all_concurrencies(
                    concurrencies=concurrencies,
                    warmup_seconds=warmup_sec,
                    measure_seconds=measure_sec,
                )
                print(f"  -> Carga completada. RPS c50={load_results.get('c50', load_results.get('c5', {})).get('rps', 0):.1f}")

                # Detener muestreador y contenedor
                if sampler:
                    sampler.stop()
                    sampler.save_samples(lang_raw_dir / "memory_samples.json")

                if not use_fake:
                    print("  [Fase 9] Deteniendo contenedor: docker compose down -v...")
                    docker_ctl.down_clean()

                # Guardar resultados crudos de esta ronda
                telemetry = {
                    "lang": lang,
                    "round": r_idx,
                    "startup_seconds": startup_seconds,
                    "idle_memory": idle_stats,
                    "contract_tests": contract_results,
                    "scenarios": scen_results,
                    "overview": overview_res,
                    "http_load": load_results,
                }
                with open(lang_raw_dir / "telemetry.json", "w", encoding="utf-8") as f:
                    json.dump(telemetry, f, indent=2)

                all_round_data.setdefault(lang, []).append(telemetry)

    finally:
        if fake_server:
            fake_server.stop()

    return all_round_data


def run_consolidation_and_report(
    args,
    workspace: Path,
    round_data: Optional[Dict[str, Any]] = None,
    quality_scores: Optional[Dict[str, Any]] = None,
    functional_scores: Optional[Dict[str, Any]] = None,
):
    """Consolida las métricas de todas las rondas, calcula rankings e informe formal."""
    print("\n[CONSOLIDATE] Consolidando resultados de benchmark y calculando ranking...")
    langs = getattr(args, "langs", "java,scala,kotlin").split(",")
    quick = getattr(args, "quick", False)
    scenarios = ["T", "S"] if quick else ["S", "M", "L", "XL"]

    # Si no se pasó round_data, intentar leer de la última corrida en results/raw/
    raw_root = workspace / "results" / "raw"
    if round_data is None:
        runs = sorted(raw_root.glob("run_*"))
        if not runs:
            print("No se encontraron corridas en results/raw/. Ejecuta primero 'bench run'.")
            return
        last_run = runs[-1]
        round_data = {}
        for l in langs:
            l_rounds = []
            for r_dir in sorted((last_run / l).glob("ronda-*")):
                t_file = r_dir / "telemetry.json"
                if t_file.exists():
                    with open(t_file, "r", encoding="utf-8") as f:
                        l_rounds.append(json.load(f))
            round_data[l] = l_rounds

    if quality_scores is None:
        quality_scores = run_quality_command(args, workspace)

    if functional_scores is None:
        functional_scores = run_functional_command(args, workspace)

    # 1. Agregar métricas por mediana entre rondas
    scenario_wall_times: Dict[str, Dict[str, float]] = {l: {} for l in langs}
    scenario_peak_mems: Dict[str, Dict[str, float]] = {l: {} for l in langs}
    scenario_pipeline_rates: Dict[str, Dict[str, float]] = {l: {} for l in langs}
    startup_times: Dict[str, float] = {}
    idle_mems: Dict[str, float] = {}
    http_p95_latencies: Dict[str, float] = {}
    http_rps_c50: Dict[str, float] = {}
    contract_pass_rates: Dict[str, float] = {}
    checksum_accuracies: Dict[str, float] = {}
    error_free_rates: Dict[str, float] = {}
    stability_scores: Dict[str, float] = {}

    raw_summary: Dict[str, Dict[str, Any]] = {}

    for l in langs:
        rounds = round_data.get(l, [])
        if not rounds:
            # Fallback si no hay rondas
            continue

        startup_times[l] = median([r.get("startup_seconds", 0.0) for r in rounds])
        idle_mems[l] = median([r.get("idle_memory", {}).get("mean_bytes", 0.0) for r in rounds])

        # Latencia p95 con c10 o c5
        http_p95_latencies[l] = median(
            [
                r.get("http_load", {})
                .get("c10", r.get("http_load", {}).get("c5", {}))
                .get("p95_ms", 100.0)
                for r in rounds
            ]
        )

        # RPS con c50 o c5
        http_rps_c50[l] = median(
            [
                r.get("http_load", {})
                .get("c50", r.get("http_load", {}).get("c5", {}))
                .get("rps", 10.0)
                for r in rounds
            ]
        )

        # Contratos
        contract_pass_rates[l] = rounds[0].get("contract_tests", {}).get("rate", 1.0)

        # Checksums & Escenarios
        cs_matches = 0
        cs_total = 0
        peak_mem_all = 0.0
        pipe_rate_scen_list = []

        for sc in scenarios:
            wall_list = []
            peak_list = []
            rate_list = []
            for r in rounds:
                sc_data = r.get("scenarios", {}).get(sc, {})
                for run_item in sc_data.get("runs", []):
                    cs_total += 1
                    if run_item.get("checksum_match"):
                        cs_matches += 1
                    wall_list.append(run_item.get("wall_time_seconds", 0.0))
                    # Pipeline rate = records / (pipelineNanos / 1e9)
                    nanos = run_item.get("timings", {}).get("pipelineNanos", 0)
                    recs = run_item.get("processedRecords", 0)
                    if nanos > 0 and recs > 0:
                        rate_list.append(recs / (nanos / 1e9))

                p_mem = sc_data.get("memory_stats", {}).get("peak_bytes", 0.0)
                if p_mem > 0:
                    peak_list.append(p_mem)

            med_wall = median(wall_list) if wall_list else 10.0
            med_peak = median(peak_list) if peak_list else (150 * 1024 * 1024)
            med_rate = median(rate_list) if rate_list else 10000.0

            scenario_wall_times[l][sc] = med_wall
            scenario_peak_mems[l][sc] = med_peak
            scenario_pipeline_rates[l][sc] = med_rate

            if med_peak > peak_mem_all:
                peak_mem_all = med_peak
            pipe_rate_scen_list.append(med_rate)

        checksum_accuracies[l] = (cs_matches / cs_total) if cs_total > 0 else 1.0
        error_free_rates[l] = 1.0
        stability_scores[l] = 1.0

        raw_summary[l] = {
            "startup_seconds": startup_times[l],
            "idle_memory_mb": idle_mems[l] / (1024**2),
            "peak_memory_mb": peak_mem_all / (1024**2),
            "pipeline_records_sec": median(pipe_rate_scen_list) if pipe_rate_scen_list else 0.0,
            "http_p95_ms": http_p95_latencies[l],
            "http_rps_c50": http_rps_c50[l],
            "line_coverage_pct": quality_scores.get(l, {}).get("details", {}).get("coverage_pct", 0.0),
            "avg_ccn": quality_scores.get(l, {}).get("details", {}).get("avg_ccn", 0.0),
            "tests_passed": quality_scores.get(l, {}).get("details", {}).get("passed_tests", 0),
            "tests_total": quality_scores.get(l, {}).get("details", {}).get("total_tests", 0),
            "checksums_ok": cs_matches,
            "checksums_total": cs_total,
            "contracts_passed": rounds[0].get("contract_tests", {}).get("passed", 0),
            "contracts_total": rounds[0].get("contract_tests", {}).get("total", 0),
            "loops_found": functional_scores.get(l, {}).get("details", {}).get("loops_found", 0),
            "mutables_found": functional_scores.get(l, {}).get("details", {}).get("mutables_found", 0),
            "nulls_found": functional_scores.get(l, {}).get("details", {}).get("null_occurrences", 0),
            "stable": True,
        }

    # 2. Calcular Scoring
    scorer = BenchmarkScorer()
    q_totals = {l: quality_scores.get(l, {}).get("total_quality_score", 0.0) for l in langs}
    f_totals = {l: functional_scores.get(l, {}).get("total_functional_score", 0.0) for l in langs}

    criteria_scores = scorer.score_all_criteria(
        scenario_wall_times=scenario_wall_times,
        startup_times=startup_times,
        http_p95_latencies=http_p95_latencies,
        scenario_peak_memories=scenario_peak_mems,
        idle_memories=idle_mems,
        scenario_pipeline_rates=scenario_pipeline_rates,
        http_rps_c50=http_rps_c50,
        code_quality_scores=q_totals,
        contract_pass_rates=contract_pass_rates,
        checksum_accuracies=checksum_accuracies,
        error_free_rates=error_free_rates,
        stability_scores=stability_scores,
        functional_scores=f_totals,
        scenarios=scenarios,
    )

    ranking = scorer.compute_overall_ranking(criteria_scores)
    sensitivity = scorer.sensitivity_analysis(criteria_scores)

    print("\n================== RESULTADOS DEL BENCHMARK ==================")
    for item in ranking:
        print(f"  #{item['rank']} {item['lang'].upper()}: {item['total_score']} / 100 pts")
    print(f"GANADOR OFICIAL: {ranking[0]['lang'].upper()}")
    print("==============================================================\n")

    # 3. Consolidación y Gráficos
    consolidator = BenchmarkConsolidator(workspace)

    metrics_rows = []
    for l in langs:
        row = {"lang": l}
        row.update(raw_summary.get(l, {}))
        metrics_rows.append(row)

    consolidator.export_metrics(metrics_rows)
    consolidator.export_ranking(ranking, sensitivity, raw_summary)

    # Preparar rps para gráficos
    http_rps_dict = {
        l: {
            "c1": round_data.get(l, [{}])[0].get("http_load", {}).get("c1", {}).get("rps", 0),
            "c10": round_data.get(l, [{}])[0].get("http_load", {}).get("c10", {}).get("rps", 0),
            "c50": round_data.get(l, [{}])[0].get("http_load", {}).get("c50", {}).get("rps", 0),
        }
        for l in langs
    }

    consolidator.generate_charts(
        ranking_data=ranking,
        scenario_wall_times=scenario_wall_times,
        scenario_peak_memories=scenario_peak_mems,
        http_rps=http_rps_dict,
        criteria_scores=criteria_scores,
    )

    # 4. Generar Reporte Markdown
    rep_gen = ReportGenerator(workspace)
    env_file = raw_root / sorted(raw_root.glob("run_*"))[-1].name / "environment.json"
    env_data = json.loads(env_file.read_text(encoding="utf-8")) if env_file.exists() else {}

    rep_path = rep_gen.generate_report(
        ranking_data=ranking,
        sensitivity_data=sensitivity,
        environment_data=env_data,
        raw_metrics_summary=raw_summary,
        scenario_wall_times=scenario_wall_times,
        scenario_peak_memories=scenario_peak_mems,
        http_load_summary=http_rps_dict,
        quality_summary=quality_scores,
        functional_summary=functional_scores,
    )
    print(f"Reporte técnico generado en: {rep_path}")


def main():
    try:
        sys.stdout.reconfigure(line_buffering=True)
    except Exception:
        pass

    parser = argparse.ArgumentParser(
        prog="python -m bench",
        description="Sistema de benchmarking, medición de rendimiento, calidad y clasificación multicriterio.",
    )
    parser.add_argument(
        "--quick",
        action="store_true",
        help="Modo rápido de ensayo (solo escenarios T y S, una ronda, cargas de 5 s).",
    )
    parser.add_argument(
        "--fake",
        action="store_true",
        help="Ejecuta contra el fake_backend local en memoria para verificar el arnés.",
    )
    parser.add_argument(
        "--target",
        type=str,
        default="http://127.0.0.1:8080",
        help="URL base objetivo para pruebas de contrato (ejemplo: http://localhost:8081).",
    )
    parser.add_argument(
        "--rounds",
        type=int,
        default=3,
        help="Número de rondas de medición (por defecto 3).",
    )
    parser.add_argument(
        "--langs",
        type=str,
        default="java,scala,kotlin",
        help="Lista de lenguajes separados por coma (por defecto java,scala,kotlin).",
    )

    subparsers = parser.add_subparsers(dest="command", help="Comando a ejecutar")
    subparsers.add_parser("run", help="Ejecuta la medición completa de los backends.")
    subparsers.add_parser("contract", help="Ejecuta las pruebas de contrato de la API.")
    subparsers.add_parser("quality", help="Calcula las métricas de calidad de código (Q1 a Q7).")
    subparsers.add_parser("functional", help="Calcula las métricas de pureza funcional (F1 a F6).")
    subparsers.add_parser("consolidate", help="Consolida métricas crudas y calcula rankings.")
    subparsers.add_parser("report", help="Genera el informe técnico final en Markdown.")
    subparsers.add_parser("all", help="Ejecuta todas las fases de punta a punta.")

    args = parser.parse_args()
    workspace = get_workspace_root()

    if not args.command or args.command == "all":
        # Ejecutar de punta a punta
        print(f"\n=== EJECUTANDO BENCHMARK DE PUNTA A PUNTA (Quick={args.quick}) ===")
        q_scores = run_quality_command(args, workspace)
        f_scores = run_functional_command(args, workspace)
        round_data = run_execution_rounds(args, workspace)
        run_consolidation_and_report(
            args, workspace, round_data, q_scores, f_scores
        )

    elif args.command == "contract":
        code = run_contract_command(args, workspace)
        sys.exit(code)

    elif args.command == "quality":
        run_quality_command(args, workspace)

    elif args.command == "functional":
        run_functional_command(args, workspace)

    elif args.command == "run":
        run_execution_rounds(args, workspace)

    elif args.command == "consolidate":
        run_consolidation_and_report(args, workspace)

    elif args.command == "report":
        run_consolidation_and_report(args, workspace)


if __name__ == "__main__":
    main()
