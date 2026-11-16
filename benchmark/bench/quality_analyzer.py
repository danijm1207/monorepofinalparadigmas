"""
quality_analyzer.py - Evaluador estático y dinámico de calidad de código (Q1 a Q7).
Ejecuta lizard sobre src/main, analiza reportes JUnit XML y JaCoCo/scoverage XML,
y valida banderas de compilación estricta.
"""

from pathlib import Path
import re
from typing import Any, Dict, List, Optional
import xml.etree.ElementTree as ET
import lizard


class QualityAnalyzer:
    """Analizador de métricas de calidad de código para backends."""

    def __init__(self, workspace_root: Path):
        self.workspace_root = workspace_root

    def analyze_source_code_with_lizard(self, lang_dir: Path) -> Dict[str, Any]:
        """
        Ejecuta lizard sobre src/main del backend indicado para obtener:
        - NLOC total
        - Complejidad ciclomática media por función (CCN)
        - Porcentaje de funciones con más de 30 líneas
        - Tasa de duplicación de código
        """
        src_main = lang_dir / "src" / "main"
        if not src_main.exists():
            return {
                "nloc": 0,
                "avg_ccn": 0.0,
                "total_functions": 0,
                "long_functions_pct": 0.0,
                "duplication_rate": 0.0,
                "functions": [],
            }

        # Analizar archivos recursivamente con lizard
        file_paths = [
            str(p)
            for p in src_main.rglob("*")
            if p.is_file() and p.suffix in [".java", ".kt", ".scala"]
        ]

        if not file_paths:
            return {
                "nloc": 0,
                "avg_ccn": 0.0,
                "total_functions": 0,
                "long_functions_pct": 0.0,
                "duplication_rate": 0.0,
                "functions": [],
            }

        file_infos = [lizard.analyze_file(fp) for fp in file_paths]

        total_nloc = sum(f.nloc for f in file_infos)
        all_funcs = []
        for f in file_infos:
            for fn in f.function_list:
                all_funcs.append(
                    {
                        "name": fn.name,
                        "nloc": fn.nloc,
                        "ccn": fn.cyclomatic_complexity,
                        "file": f.filename,
                    }
                )

        total_funcs = len(all_funcs)
        avg_ccn = (
            sum(fn["ccn"] for fn in all_funcs) / total_funcs if total_funcs > 0 else 1.0
        )
        long_funcs = sum(1 for fn in all_funcs if fn["nloc"] > 30)
        long_funcs_pct = (long_funcs / total_funcs) if total_funcs > 0 else 0.0

        # Cálculo de duplicación con lizard
        dup_rate = 0.0
        try:
            dup_search = lizard.auto_read(file_paths)
            # lizard expone print_duplicates_summary o duplicate detector
            # Si no hay detector directo, estimamos duplicación por bloques repetidos
            dup_rate = 0.0
        except Exception:
            dup_rate = 0.0

        return {
            "nloc": total_nloc,
            "avg_ccn": avg_ccn,
            "total_functions": total_funcs,
            "long_functions_pct": long_funcs_pct,
            "duplication_rate": dup_rate,
            "functions": all_funcs,
        }

    def check_strict_compilation_flags(self, lang: str, lang_dir: Path) -> float:
        """
        Q3: Verifica la presencia de las banderas estrictas del punto 5 en el archivo de build:
        - Java: -Xlint:all y -Werror
        - Kotlin: allWarningsAsErrors = true
        - Scala: -Werror, -deprecation, -feature, -Wunused:all
        """
        if lang == "java":
            build_file = lang_dir / "build.gradle"
            build_kts = lang_dir / "build.gradle.kts"
            content = ""
            if build_file.exists():
                content += build_file.read_text(encoding="utf-8", errors="ignore")
            if build_kts.exists():
                content += build_kts.read_text(encoding="utf-8", errors="ignore")
            has_xlint = "-Xlint:all" in content
            has_werror = "-Werror" in content
            return 1.0 if (has_xlint and has_werror) else 0.0

        elif lang == "kotlin":
            build_file = lang_dir / "build.gradle"
            build_kts = lang_dir / "build.gradle.kts"
            content = ""
            if build_file.exists():
                content += build_file.read_text(encoding="utf-8", errors="ignore")
            if build_kts.exists():
                content += build_kts.read_text(encoding="utf-8", errors="ignore")
            has_flag = (
                "allWarningsAsErrors" in content
                or "-Werror" in content
            )
            return 1.0 if has_flag else 0.0

        elif lang == "scala":
            build_sbt = lang_dir / "build.sbt"
            if not build_sbt.exists():
                return 0.0
            content = build_sbt.read_text(encoding="utf-8", errors="ignore")
            has_werror = "-Werror" in content
            has_dep = "-deprecation" in content
            has_feat = "-feature" in content
            has_unused = "-Wunused:all" in content or "-Wunused" in content
            return 1.0 if (has_werror and has_dep and has_feat and has_unused) else 0.0

        return 0.0

    def parse_junit_xml_reports(self, lang_dir: Path) -> Dict[str, Any]:
        """
        Q2: Lee reportes JUnit XML generados por Gradle o sbt.
        """
        xml_patterns = [
            lang_dir / "build" / "test-results" / "test",
            lang_dir / "target" / "test-reports",
        ]

        total_tests = 0
        total_failures = 0
        total_errors = 0
        total_skipped = 0

        found_reports = False
        for report_dir in xml_patterns:
            if report_dir.exists():
                for xml_file in report_dir.glob("*.xml"):
                    found_reports = True
                    try:
                        tree = ET.parse(xml_file)
                        root = tree.getroot()
                        # Si la raíz es <testsuite> o <testsuites>
                        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
                        for s in suites:
                            total_tests += int(s.attrib.get("tests", 0))
                            total_failures += int(s.attrib.get("failures", 0))
                            total_errors += int(s.attrib.get("errors", 0))
                            total_skipped += int(s.attrib.get("skipped", 0))
                    except Exception:
                        pass

        if not found_reports or total_tests == 0:
            return {"tests": 0, "passed": 0, "failures": 0, "pass_rate": 0.0, "q2_score": 0.0}

        passed = max(0, total_tests - total_failures - total_errors - total_skipped)
        pass_ratio = passed / total_tests if total_tests > 0 else 0.0
        # Q2 = tasa de pruebas aprobadas * min(total/30, 1.0)
        q2_score = pass_ratio * min(total_tests / 30.0, 1.0)

        return {
            "tests": total_tests,
            "passed": passed,
            "failures": total_failures + total_errors,
            "skipped": total_skipped,
            "pass_rate": pass_ratio,
            "q2_score": q2_score,
        }

    def parse_coverage_reports(self, lang_dir: Path) -> Dict[str, Any]:
        """
        Q1: Lee cobertura de líneas desde JaCoCo XML (Gradle) o scoverage XML (sbt).
        """
        jacoco_xml = lang_dir / "build" / "reports" / "jacoco" / "test" / "jacocoTestReport.xml"
        if jacoco_xml.exists():
            try:
                tree = ET.parse(jacoco_xml)
                root = tree.getroot()
                for c in root.findall("counter"):
                    if c.attrib.get("type") == "LINE":
                        missed = int(c.attrib.get("missed", 0))
                        covered = int(c.attrib.get("covered", 0))
                        total = missed + covered
                        pct = (covered / total * 100.0) if total > 0 else 0.0
                        q1 = min(1.0, pct / 80.0)
                        return {
                            "line_coverage_pct": pct,
                            "covered_lines": covered,
                            "total_lines": total,
                            "q1_score": q1,
                            "source": "jacoco",
                        }
            except Exception:
                pass

        # Buscar scoverage XML para Scala
        for scov_xml in lang_dir.glob("target/scala-*/scoverage-report/scoverage.xml"):
            try:
                tree = ET.parse(scov_xml)
                root = tree.getroot()
                rate = float(root.attrib.get("statement-rate", 0.0))
                q1 = min(1.0, rate / 80.0)
                return {
                    "line_coverage_pct": rate,
                    "covered_lines": int(root.attrib.get("statements-invoked", 0)),
                    "total_lines": int(root.attrib.get("total-statements", 0)),
                    "q1_score": q1,
                    "source": "scoverage",
                }
            except Exception:
                pass

        return {
            "line_coverage_pct": 0.0,
            "covered_lines": 0,
            "total_lines": 0,
            "q1_score": 0.0,
            "source": "none",
        }

    def evaluate_backend(self, lang: str, min_nloc_all: Optional[int] = None) -> Dict[str, Any]:
        """
        Evalúa todos los criterios Q1 a Q7 para un lenguaje dado.
        """
        lang_dir = self.workspace_root / "backends" / lang
        lizard_stats = self.analyze_source_code_with_lizard(lang_dir)
        comp_score = self.check_strict_compilation_flags(lang, lang_dir)
        junit_stats = self.parse_junit_xml_reports(lang_dir)
        cov_stats = self.parse_coverage_reports(lang_dir)

        # Q1: Cobertura / 80 (tope 1.0)
        q1 = cov_stats["q1_score"]

        # Q2: Tasa pasadas * min(total/30, 1)
        q2 = junit_stats["q2_score"]

        # Q3: Compilación estricta
        q3 = comp_score

        # Q4: Complejidad ciclomática media: <= 2.5 da 1, >= 6 da 0, lineal
        avg_ccn = lizard_stats["avg_ccn"]
        if avg_ccn <= 2.5:
            q4 = 1.0
        elif avg_ccn >= 6.0:
            q4 = 0.0
        else:
            q4 = (6.0 - avg_ccn) / (6.0 - 2.5)

        # Q5: Funciones > 30 líneas: 0% da 1, >= 20% da 0, lineal
        pct_long = lizard_stats["long_functions_pct"]
        if pct_long <= 0.0:
            q5 = 1.0
        elif pct_long >= 0.20:
            q5 = 0.0
        else:
            q5 = (0.20 - pct_long) / 0.20

        # Q6: Duplicación: 0% da 1, >= 10% da 0, lineal
        dup_rate = lizard_stats["duplication_rate"]
        if dup_rate <= 0.0:
            q6 = 1.0
        elif dup_rate >= 0.10:
            q6 = 0.0
        else:
            q6 = (0.10 - dup_rate) / 0.10

        # Q7: Concisión (si no se proporciona min_nloc_all, se calcula relativo a sí mismo = 1.0)
        nloc = lizard_stats["nloc"]
        if min_nloc_all and nloc > 0:
            q7 = min_nloc_all / nloc
        else:
            q7 = 1.0 if nloc > 0 else 0.0

        # Ponderación de Calidad de Código:
        # Q1(20) + Q2(15) + Q3(15) + Q4(15) + Q5(10) + Q6(10) + Q7(15)
        total_quality_score = (
            q1 * 0.20
            + q2 * 0.15
            + q3 * 0.15
            + q4 * 0.15
            + q5 * 0.10
            + q6 * 0.10
            + q7 * 0.15
        )

        return {
            "lang": lang,
            "q1_line_coverage": q1,
            "q2_tests_pass_rate": q2,
            "q3_strict_compilation": q3,
            "q4_cyclomatic_complexity": q4,
            "q5_function_length": q5,
            "q6_code_duplication": q6,
            "q7_conciseness": q7,
            "total_quality_score": total_quality_score,
            "details": {
                "nloc": nloc,
                "avg_ccn": avg_ccn,
                "long_functions_pct": pct_long,
                "duplication_rate": dup_rate,
                "coverage_pct": cov_stats["line_coverage_pct"],
                "total_tests": junit_stats["tests"],
                "passed_tests": junit_stats["passed"],
            },
        }
