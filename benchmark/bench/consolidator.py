"""
consolidator.py - Consolidación de métricas, generación de tablas y gráficos visuales.
Genera metrics.csv, metrics.json, ranking.json, ranking.md y gráficos PNG en results/charts/.
"""

import json
from pathlib import Path
from typing import Any, Dict, List, Tuple
import matplotlib
matplotlib.use("Agg")  # Backend no interactivo para generación de gráficos en segundo plano
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


class BenchmarkConsolidator:
    """Consolidador de resultados de benchmarking."""

    def __init__(self, workspace_root: Path):
        self.workspace_root = workspace_root
        self.results_dir = workspace_root / "results"
        self.consolidated_dir = self.results_dir / "consolidated"
        self.charts_dir = self.results_dir / "charts"

        self.consolidated_dir.mkdir(parents=True, exist_ok=True)
        self.charts_dir.mkdir(parents=True, exist_ok=True)

    def export_metrics(
        self, metrics_data: List[Dict[str, Any]]
    ) -> Tuple[Path, Path]:
        """Exporta metrics.json y metrics.csv."""
        json_path = self.consolidated_dir / "metrics.json"
        csv_path = self.consolidated_dir / "metrics.csv"

        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(metrics_data, f, indent=2)

        df = pd.DataFrame(metrics_data)
        df.to_csv(csv_path, index=False)

        return json_path, csv_path

    def export_ranking(
        self,
        ranking_data: List[Dict[str, Any]],
        sensitivity_data: Dict[str, Any],
        raw_metrics_summary: Dict[str, Dict[str, Any]],
    ) -> Tuple[Path, Path]:
        """Exporta ranking.json y ranking.md con desglose y métricas justificativas."""
        json_path = self.consolidated_dir / "ranking.json"
        md_path = self.consolidated_dir / "ranking.md"

        full_ranking_bundle = {
            "winner": ranking_data[0]["lang"],
            "ranking": ranking_data,
            "sensitivity": sensitivity_data,
        }

        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(full_ranking_bundle, f, indent=2)

        # Generar Markdown estructurado
        md_lines = [
            "# Clasificación Oficial del Benchmark Comparativo\n",
            f"**Ganador Determinado Automáticamente:** `{ranking_data[0]['lang'].upper()}` con **{ranking_data[0]['total_score']} / 100** puntos.\n",
            "## 1. Tabla de Posiciones Finales\n",
            "| Posición | Lenguaje | Puntaje Total (0-100) | Tiempo (25%) | Memoria (20%) | Rendimiento (20%) | Calidad (15%) | Confiabilidad (10%) | Funcional (10%) |",
            "|:---:|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|",
        ]

        for item in ranking_data:
            c = item["criteria"]
            md_lines.append(
                f"| **#{item['rank']}** | **{item['lang'].capitalize()}** | **{item['total_score']}** | "
                f"{c.get('execution_time', 0.0)*100:.1f} | {c.get('memory_consumption', 0.0)*100:.1f} | "
                f"{c.get('throughput', 0.0)*100:.1f} | {c.get('code_quality', 0.0)*100:.1f} | "
                f"{c.get('reliability', 0.0)*100:.1f} | {c.get('functional_usage', 0.0)*100:.1f} |"
            )

        md_lines.append("\n## 2. Justificación Técnica por Métricas Instrumentadas\n")
        for item in ranking_data:
            lang = item["lang"]
            summary = raw_metrics_summary.get(lang, {})
            md_lines.append(f"### {item['rank']}. {lang.capitalize()} ({item['total_score']} pts)")
            md_lines.append(f"- **Tiempo de Arranque:** {summary.get('startup_seconds', 0.0):.2f} s")
            md_lines.append(f"- **Memoria en Reposo:** {summary.get('idle_memory_mb', 0.0):.1f} MB | **Pico Máximo:** {summary.get('peak_memory_mb', 0.0):.1f} MB")
            md_lines.append(f"- **Rendimiento Pipeline:** {summary.get('pipeline_records_sec', 0.0):,.0f} reg/s | **Throughput HTTP (c50):** {summary.get('http_rps_c50', 0.0):.1f} RPS")
            md_lines.append(f"- **Calidad de Código:** Cobertura {summary.get('line_coverage_pct', 0.0):.1f}%, CCN Medio {summary.get('avg_ccn', 0.0):.2f}, Tests {summary.get('tests_passed', 0)}/{summary.get('tests_total', 0)}")
            md_lines.append(f"- **Confiabilidad:** Checksums {summary.get('checksums_ok', 0)}/{summary.get('checksums_total', 0)}, Tests Contrato {summary.get('contracts_passed', 0)}/{summary.get('contracts_total', 0)}")
            md_lines.append(f"- **Pureza Funcional:** Bucles encontrados: {summary.get('loops_found', 0)}, Mutables: {summary.get('mutables_found', 0)}, Nulos: {summary.get('nulls_found', 0)}\n")

        md_lines.append("## 3. Análisis de Sensibilidad\n")
        sens = sensitivity_data
        md_lines.append(f"- **¿Cambia el ganador bajo ponderaciones alternativas?:** {'SÍ' if sens.get('winner_changed') else 'NO'}")
        md_lines.append(f"- **Ganador con Pesos Iguales (16.6% c/u):** `{sens.get('scenarios', {}).get('equal_weights', {}).get('winner', 'N/A')}`")
        md_lines.append(f"- **Ganador con Peso Doble en Tiempo y Rendimiento:** `{sens.get('scenarios', {}).get('double_perf_and_time', {}).get('winner', 'N/A')}`\n")

        with open(md_path, "w", encoding="utf-8") as f:
            f.write("\n".join(md_lines))

        return json_path, md_path

    def generate_charts(
        self,
        ranking_data: List[Dict[str, Any]],
        scenario_wall_times: Dict[str, Dict[str, float]],
        scenario_peak_memories: Dict[str, Dict[str, float]],
        http_rps: Dict[str, Dict[str, float]],
        criteria_scores: Dict[str, Dict[str, float]],
    ) -> List[Path]:
        """Genera suite completa de gráficos en PNG para el informe."""
        generated_charts: List[Path] = []
        langs = [item["lang"] for item in ranking_data]
        colors = {"java": "#E76F51", "scala": "#2A9D8F", "kotlin": "#F4A261"}

        # 1. Gráfico de Ranking Final Compuesto (Barra Horizontal)
        plt.figure(figsize=(9, 4.5), dpi=150)
        langs_rev = list(reversed(langs))
        scores_rev = [
            next(item["total_score"] for item in ranking_data if item["lang"] == l)
            for l in langs_rev
        ]
        bar_colors = [colors.get(l, "#457B9D") for l in langs_rev]

        bars = plt.barh(langs_rev, scores_rev, color=bar_colors, height=0.55)
        plt.xlabel("Puntaje Global Ponderado (0 - 100)", fontsize=11, fontweight="bold")
        plt.title("Clasificación Final Multicriterio", fontsize=13, fontweight="bold")
        plt.xlim(0, 105)
        for bar in bars:
            width = bar.get_width()
            plt.text(
                width + 1.5,
                bar.get_y() + bar.get_height() / 2,
                f"{width:.1f} pts",
                va="center",
                fontweight="bold",
            )
        plt.grid(axis="x", linestyle="--", alpha=0.5)
        plt.tight_layout()
        chart_p1 = self.charts_dir / "final_ranking.png"
        plt.savefig(chart_p1)
        plt.close()
        generated_charts.append(chart_p1)

        # 2. Gráfico Radar / Comparativo de los 6 Criterios
        plt.figure(figsize=(10, 5), dpi=150)
        criteria_keys = [
            "execution_time",
            "memory_consumption",
            "throughput",
            "code_quality",
            "reliability",
            "functional_usage",
        ]
        criteria_labels = [
            "Tiempo\n(25%)",
            "Memoria\n(20%)",
            "Rendimiento\n(20%)",
            "Calidad\n(15%)",
            "Confiabilidad\n(10%)",
            "Funcional\n(10%)",
        ]

        x = np.arange(len(criteria_keys))
        width = 0.25
        for idx, lang in enumerate(langs):
            y_vals = [
                criteria_scores.get(crit, {}).get(lang, 0.0) * 100 for crit in criteria_keys
            ]
            plt.bar(
                x + (idx - 1) * width,
                y_vals,
                width,
                label=lang.capitalize(),
                color=colors.get(lang, "#264653"),
            )

        plt.ylabel("Puntuación Normalizada (0 - 100)", fontsize=11, fontweight="bold")
        plt.title("Desglose por Criterio de Evaluación", fontsize=13, fontweight="bold")
        plt.xticks(x, criteria_labels, fontsize=10)
        plt.ylim(0, 110)
        plt.legend(loc="upper right")
        plt.grid(axis="y", linestyle="--", alpha=0.5)
        plt.tight_layout()
        chart_p2 = self.charts_dir / "criteria_breakdown.png"
        plt.savefig(chart_p2)
        plt.close()
        generated_charts.append(chart_p2)

        # 3. Gráfico de Tiempos de Ejecución por Escenario
        plt.figure(figsize=(9, 4.5), dpi=150)
        scenarios = list(next(iter(scenario_wall_times.values())).keys()) if scenario_wall_times else []
        if scenarios:
            for lang in langs:
                times = [scenario_wall_times.get(lang, {}).get(sc, 0.0) for sc in scenarios]
                plt.plot(
                    scenarios,
                    times,
                    marker="o",
                    linewidth=2,
                    label=lang.capitalize(),
                    color=colors.get(lang, "#264653"),
                )
            plt.xlabel("Escenario de Benchmark", fontsize=11, fontweight="bold")
            plt.ylabel("Tiempo de Pared (segundos)", fontsize=11, fontweight="bold")
            plt.title("Tiempo de Ejecución por Escenario", fontsize=13, fontweight="bold")
            plt.legend()
            plt.grid(True, linestyle="--", alpha=0.5)
            plt.tight_layout()
            chart_p3 = self.charts_dir / "execution_time.png"
            plt.savefig(chart_p3)
            plt.close()
            generated_charts.append(chart_p3)

        # 4. Gráfico de Consumo de Memoria Pico (MB)
        plt.figure(figsize=(9, 4.5), dpi=150)
        if scenarios:
            x_sc = np.arange(len(scenarios))
            for idx, lang in enumerate(langs):
                mems = [
                    scenario_peak_memories.get(lang, {}).get(sc, 0.0) / (1024**2)
                    for sc in scenarios
                ]
                plt.bar(
                    x_sc + (idx - 1) * width,
                    mems,
                    width,
                    label=lang.capitalize(),
                    color=colors.get(lang, "#264653"),
                )
            plt.xlabel("Escenario de Benchmark", fontsize=11, fontweight="bold")
            plt.ylabel("Pico de Memoria (MB)", fontsize=11, fontweight="bold")
            plt.title("Consumo de Memoria Pico del Contenedor", fontsize=13, fontweight="bold")
            plt.xticks(x_sc, scenarios)
            plt.legend()
            plt.grid(axis="y", linestyle="--", alpha=0.5)
            plt.tight_layout()
            chart_p4 = self.charts_dir / "memory_consumption.png"
            plt.savefig(chart_p4)
            plt.close()
            generated_charts.append(chart_p4)

        # 5. Throughput HTTP (RPS por concurrencia)
        plt.figure(figsize=(9, 4.5), dpi=150)
        concurrencies = list(next(iter(http_rps.values())).keys()) if http_rps else []
        if concurrencies:
            for lang in langs:
                rps_vals = [http_rps.get(lang, {}).get(c, 0.0) for c in concurrencies]
                plt.plot(
                    concurrencies,
                    rps_vals,
                    marker="s",
                    linewidth=2,
                    label=lang.capitalize(),
                    color=colors.get(lang, "#264653"),
                )
            plt.xlabel("Nivel de Concurrencia HTTP", fontsize=11, fontweight="bold")
            plt.ylabel("Solicitudes por Segundo (RPS)", fontsize=11, fontweight="bold")
            plt.title("Rendimiento HTTP bajo Concurrencia", fontsize=13, fontweight="bold")
            plt.legend()
            plt.grid(True, linestyle="--", alpha=0.5)
            plt.tight_layout()
            chart_p5 = self.charts_dir / "http_throughput.png"
            plt.savefig(chart_p5)
            plt.close()
            generated_charts.append(chart_p5)

        return generated_charts
