"""
functional_analyzer.py - Evaluador estático de adaptación y pureza funcional (F1 a F6).
Analiza exclusivamente los archivos de producción:
  analytics/AnalyticsPipeline.*
  analytics/ReportFormatter.*
siguiendo las reglas configuradas en functional_rules.yaml.
"""

from pathlib import Path
import re
from typing import Any, Dict, List, Optional
import yaml
import lizard


class FunctionalAnalyzer:
    """Analizador estático de pureza funcional."""

    def __init__(self, workspace_root: Path, config_path: Optional[Path] = None):
        self.workspace_root = workspace_root
        if config_path is None:
            config_path = (
                workspace_root / "benchmark" / "config" / "functional_rules.yaml"
            )
        self.config_path = config_path
        self.rules: Dict[str, Any] = {}
        if self.config_path.exists():
            with open(self.config_path, "r", encoding="utf-8") as f:
                self.rules = yaml.safe_load(f)

    def find_analytics_files(self, lang: str) -> List[Path]:
        """Localiza AnalyticsPipeline.* y ReportFormatter.* para el backend dado."""
        lang_dir = self.workspace_root / "backends" / lang / "src" / "main"
        if not lang_dir.exists():
            return []

        target_names = ["AnalyticsPipeline", "ReportFormatter"]
        extensions = [".java", ".kt", ".scala"]
        found: List[Path] = []

        for p in lang_dir.rglob("*"):
            if p.is_file() and p.suffix in extensions:
                if any(t in p.stem for t in target_names):
                    found.append(p)

        return found

    def strip_comments_and_strings(self, code: str) -> str:
        """Remueve comentarios y cadenas literales para evitar falsos positivos en regex."""
        # Remover comentarios multilínea /* ... */
        code = re.sub(r"/\*[\s\S]*?\*/", "", code)
        # Remover comentarios de una línea // ...
        code = re.sub(r"//.*", "", code)
        # Remover strings literales " ... "
        code = re.sub(r'"(\\.|[^"\\])*"', '""', code)
        return code

    def evaluate_backend(self, lang: str) -> Dict[str, Any]:
        """Evalúa los subcriterios F1 a F6 para el lenguaje especificado."""
        files = self.find_analytics_files(lang)
        if not files:
            return {
                "lang": lang,
                "f1_no_explicit_loops": 0.0,
                "f2_immutable_state": 0.0,
                "f3_operations_coverage": 0.0,
                "f4_lambda_density": 0.0,
                "f5_null_safety": 0.0,
                "f6_imports_purity": 0.0,
                "total_functional_score": 0.0,
                "details": {
                    "files_found": 0,
                    "nloc": 0,
                    "loops_found": 0,
                    "mutables_found": 0,
                    "null_occurrences": 0,
                    "operations_found": 0,
                    "lambda_count": 0,
                    "lambdas_per_100_nloc": 0.0,
                },
            }

        total_nloc = 0
        raw_code = ""
        for f in files:
            txt = f.read_text(encoding="utf-8", errors="ignore")
            raw_code += txt + "\n"
            file_info = lizard.analyze_file.analyze_source_code(f.name, txt)
            total_nloc += file_info.nloc

        clean_code = self.strip_comments_and_strings(raw_code)

        lang_rules = self.rules.get("language_patterns", {}).get(lang, {})
        prohibited_imports = self.rules.get("prohibited_imports", [])
        required_ops = self.rules.get("required_operations", [])

        # F1: Cero bucles explícitos (for / while)
        loop_patterns = lang_rules.get("loops", [])
        loop_count = 0
        for pat in loop_patterns:
            loop_count += len(re.findall(pat, clean_code))
        f1 = max(0.0, 1.0 - 0.2 * loop_count)

        # F2: Cero estado mutable (var, colecciones mutables)
        mutable_patterns = lang_rules.get("mutables", [])
        mutable_count = 0
        for pat in mutable_patterns:
            mutable_count += len(re.findall(pat, clean_code))
        f2 = max(0.0, 1.0 - 0.2 * mutable_count)

        # F3: Cobertura de operaciones requeridas (map, filter, group, reduce/fold/sum, flatMap, sort)
        ops_found = 0
        ops_details = {}
        for op in required_ops:
            op_name = op["name"]
            patterns = op.get("patterns", {}).get(lang, [])
            matched = any(re.search(p, raw_code) for p in patterns)
            ops_details[op_name] = matched
            if matched:
                ops_found += 1
        f3 = ops_found / 6.0 if len(required_ops) >= 6 else 0.0

        # F4: Densidad de lambdas por cada 100 NLOC
        lambda_indicators = lang_rules.get("lambda_indicators", [])
        lambda_count = 0
        for pat in lambda_indicators:
            lambda_count += len(re.findall(pat, clean_code))
        lambdas_per_100_nloc = (
            (lambda_count / total_nloc * 100.0) if total_nloc > 0 else 0.0
        )
        f4 = min(1.0, lambdas_per_100_nloc / 10.0)

        # F5: Manejo seguro de nulos (cero null literales o !!)
        null_patterns = lang_rules.get("null_safety", [])
        null_count = 0
        for pat in null_patterns:
            null_count += len(re.findall(pat, clean_code))
        f5 = max(0.0, 1.0 - 0.2 * null_count)

        # F6: Pureza de imports (ninguno de los prohibidos)
        prohibited_found = []
        for imp in prohibited_imports:
            # Buscar import ...
            if re.search(rf"\bimport\s+.*{re.escape(imp)}", raw_code):
                prohibited_found.append(imp)
        f6 = 1.0 if len(prohibited_found) == 0 else 0.0

        # Puntuación global funcional:
        # F1(25) + F2(25) + F3(20) + F4(10) + F5(10) + F6(10)
        total_functional_score = (
            f1 * 0.25
            + f2 * 0.25
            + f3 * 0.20
            + f4 * 0.10
            + f5 * 0.10
            + f6 * 0.10
        )

        return {
            "lang": lang,
            "f1_no_explicit_loops": f1,
            "f2_immutable_state": f2,
            "f3_operations_coverage": f3,
            "f4_lambda_density": f4,
            "f5_null_safety": f5,
            "f6_imports_purity": f6,
            "total_functional_score": total_functional_score,
            "details": {
                "files_analyzed": [str(p.relative_to(self.workspace_root)) for p in files],
                "nloc": total_nloc,
                "loops_found": loop_count,
                "mutables_found": mutable_count,
                "operations_found": ops_found,
                "operations_details": ops_details,
                "lambda_count": lambda_count,
                "lambdas_per_100_nloc": lambdas_per_100_nloc,
                "null_occurrences": null_count,
                "prohibited_imports_found": prohibited_found,
            },
        }
