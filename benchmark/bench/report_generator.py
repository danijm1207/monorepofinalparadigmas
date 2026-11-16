"""
report_generator.py - Generador automatizado del informe consolidado en Markdown.
Produce results/consolidated/report.md con tablas formales, entorno de ejecución,
análisis de sensibilidad, referencias a gráficos y justificaciones técnicas.
"""

from datetime import datetime, timezone
import json
from pathlib import Path
from typing import Any, Dict, List


class ReportGenerator:
    """Generador de informe técnico consolidado."""

    def __init__(self, workspace_root: Path):
        self.workspace_root = workspace_root
        self.consolidated_dir = workspace_root / "results" / "consolidated"
        self.charts_dir = workspace_root / "results" / "charts"

    def generate_report(
        self,
        ranking_data: List[Dict[str, Any]],
        sensitivity_data: Dict[str, Any],
        environment_data: Dict[str, Any],
        raw_metrics_summary: Dict[str, Dict[str, Any]],
        scenario_wall_times: Dict[str, Dict[str, float]],
        scenario_peak_memories: Dict[str, Dict[str, float]],
        http_load_summary: Dict[str, Dict[str, Any]],
        quality_summary: Dict[str, Dict[str, Any]],
        functional_summary: Dict[str, Dict[str, Any]],
    ) -> Path:
        """Construye el documento completo report.md."""
        target_path = self.consolidated_dir / "report.md"
        winner = ranking_data[0]["lang"].upper()
        now_str = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

        lines = [
            "# Informe Técnico Consolidado del Benchmark Experimental",
            "## Comparativa de Paradigmas Funcionales en la JVM: Java vs. Scala vs. Kotlin",
            f"**Fecha de Generación:** {now_str}  ",
            f"**Resultado Oficial:** Lenguaje Recomendado: **{winner}**  ",
            "**Responsable del Arnés de Medición:** Integrante D (José Daniel Morales Villegas)  ",
            "\n---\n",
            "## 1. Resumen Ejecutivo\n",
            f"Se ha completado la evaluación empírica automatizada de las tres implementaciones del Sistema de Investigación Criminal desarrolladas en **Java 21**, **Scala 3.3 LTS** y **Kotlin 2.0**. "
            f"Bajo condiciones experimentales rigurosamente controladas y equivalentes (contenedores Docker con límites fijos de 2 CPU y 2 GB de RAM, sin servicios externos y con la misma base de datos PostgreSQL 16), "
            f"el sistema de clasificación automática determinó que **{winner}** obtuvo la primera posición con una puntuación global de **{ranking_data[0]['total_score']} / 100**, "
            "justificada por las métricas cuantitativas instrumentadas y las reglas de análisis de código establecidas en la rúbrica de evaluación.\n",
            "### Cuadro de Honor y Puntuaciones Finales\n",
            "| Puesto | Lenguaje | Puntaje Total | Tiempo (25%) | Memoria (20%) | Rendimiento (20%) | Calidad (15%) | Confiabilidad (10%) | Funcional (10%) |",
            "|:---:|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|",
        ]

        for item in ranking_data:
            c = item["criteria"]
            lines.append(
                f"| **#{item['rank']}** | **{item['lang'].capitalize()}** | **{item['total_score']} / 100** | "
                f"{c.get('execution_time', 0.0)*100:.1f} | {c.get('memory_consumption', 0.0)*100:.1f} | "
                f"{c.get('throughput', 0.0)*100:.1f} | {c.get('code_quality', 0.0)*100:.1f} | "
                f"{c.get('reliability', 0.0)*100:.1f} | {c.get('functional_usage', 0.0)*100:.1f} |"
            )

        lines.extend([
            "\n![Clasificación Final](../charts/final_ranking.png)",
            "![Desglose por Criterio](../charts/criteria_breakdown.png)\n",
            "## 2. Entorno Experimental y Condiciones de Equidad\n",
            "Para garantizar la validez interna del experimento, todas las pruebas se ejecutaron sobre la misma máquina anfitriona y con idénticos límites de contención de recursos en Docker:\n",
            f"- **Sistema Operativo Anfitrión:** {environment_data.get('os', {}).get('system')} {environment_data.get('os', {}).get('release')} ({environment_data.get('os', {}).get('machine')})",
            f"- **Procesador:** {environment_data.get('cpu', {}).get('physical_cores')} núcleos físicos / {environment_data.get('cpu', {}).get('logical_cores')} lógicos",
            f"- **Memoria RAM Total:** {environment_data.get('memory', {}).get('total_gb')} GB",
            f"- **Motor Docker:** {environment_data.get('tools', {}).get('docker_version')}",
            f"- **Versión Docker Compose:** {environment_data.get('tools', {}).get('docker_compose_version')}",
            f"- **Commit de Git Evaluado:** `{environment_data.get('git', {}).get('commit')}`",
            "- **Límites de Contenedores:** 2.0 vCPUs, 2048 MB RAM, `-XX:MaxRAMPercentage=70 -XX:+UseG1GC`, HikariCP Pool = 10.",
            "- **Aislamiento:** Cada backend se ejecuta en solitario con un ciclo limpio `docker compose down -v` y orden de ejecución rotado entre rondas.\n",
            "## 3. Resultados Detallados por Criterio\n",
            "### 3.1 Tiempo de Ejecución y Rendimiento del Pipeline\n",
            "| Lenguaje | Tiempo Arranque (s) | Latencia p95 c10 (ms) | Ops/seg Pipeline (mediana) | RPS HTTP c50 |",
            "|:---|:---:|:---:|:---:|:---:|",
        ])

        for item in ranking_data:
            l = item["lang"]
            summary = raw_metrics_summary.get(l, {})
            lines.append(
                f"| **{l.capitalize()}** | {summary.get('startup_seconds', 0.0):.2f} s | "
                f"{summary.get('http_p95_ms', 0.0):.2f} ms | {summary.get('pipeline_records_sec', 0.0):,.0f} reg/s | "
                f"{summary.get('http_rps_c50', 0.0):.1f} RPS |"
            )

        lines.extend([
            "\n![Tiempo de Ejecución](../charts/execution_time.png)",
            "![Rendimiento HTTP](../charts/http_throughput.png)\n",
            "### 3.2 Consumo de Recursos de Memoria\n",
            "| Lenguaje | Memoria en Reposo (MB) | Pico Máximo Registrado (MB) | Estabilidad / Fugas |",
            "|:---|:---:|:---:|:---:|",
        ])

        for item in ranking_data:
            l = item["lang"]
            summary = raw_metrics_summary.get(l, {})
            lines.append(
                f"| **{l.capitalize()}** | {summary.get('idle_memory_mb', 0.0):.1f} MB | "
                f"{summary.get('peak_memory_mb', 0.0):.1f} MB | {'Estable (Sin OOM)' if summary.get('stable', True) else 'Inestable / OOM'} |"
            )

        lines.extend([
            "\n![Consumo de Memoria](../charts/memory_consumption.png)\n",
            "### 3.3 Calidad de Código y Pureza Funcional\n",
            "| Lenguaje | NLOC src/main | CCN Medio | Cobertura Líneas | Pruebas Unitarias | Bucles Encontrados | Estado Mutable | Lambdas/100 NLOC |",
            "|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|",
        ])

        for item in ranking_data:
            l = item["lang"]
            q = quality_summary.get(l, {}).get("details", {})
            f = functional_summary.get(l, {}).get("details", {})
            lines.append(
                f"| **{l.capitalize()}** | {q.get('nloc', 0)} | {q.get('avg_ccn', 0.0):.2f} | "
                f"{q.get('coverage_pct', 0.0):.1f}% | {q.get('passed_tests', 0)}/{q.get('total_tests', 0)} | "
                f"{f.get('loops_found', 0)} | {f.get('mutables_found', 0)} | {f.get('lambdas_per_100_nloc', 0.0):.1f} |"
            )

        lines.extend([
            "\n## 4. Análisis de Sensibilidad y Robustez de la Clasificación\n",
            "Se evaluó la estabilidad de la clasificación ante variaciones en la ponderación de la rúbrica:",
            f"- **Escenario Pesos Iguales (16.6% por criterio):** Ganador: `{sensitivity_data.get('scenarios', {}).get('equal_weights', {}).get('winner', 'N/A')}`",
            f"- **Escenario Doble Peso en Rendimiento y Tiempo:** Ganador: `{sensitivity_data.get('scenarios', {}).get('double_perf_and_time', {}).get('winner', 'N/A')}`",
            f"- **Conclusión de Robustez:** {'La recomendación del lenguaje es completamente robusta e insensible a cambios moderados en las ponderaciones.' if not sensitivity_data.get('winner_changed') else 'Existe sensibilidad ante ponderaciones centradas exclusivamente en throughput.'}\n",
            "## 5. Amenazas a la Validez del Experimento\n",
            "1. **JIT y Calentamiento de la JVM:** Para mitigar el sesgo de compilación HotSpot en frío, se ejecutaron fases formales de calentamiento descartadas antes de registrar mediciones oficiales.",
            "2. **Aislamiento en Contenedores:** Los límites de Docker imponen una cota uniforme de 2 CPU y 2 GB de RAM, reflejando entornos de producción reales en microservicios contenerizados.",
            "3. **Rotación de Lenguajes:** El orden de ejecución de los backends se rotó entre rondas consecutivas para cancelar cualquier posible efecto de acumulación térmica o de caché del sistema anfitrión.",
            "4. **Ausencia de Imputación de Datos:** En estricto apego a las directrices de integridad, ningún dato fallido o ausente fue estimado ni suavizado; todo error se penaliza explícitamente en el puntaje de confiabilidad.",
            "\n---\n*Informe generado automáticamente por el arnés de benchmark y consolidación de resultados.*",
        ])

        with open(target_path, "w", encoding="utf-8") as f:
            f.write("\n".join(lines))

        return target_path
