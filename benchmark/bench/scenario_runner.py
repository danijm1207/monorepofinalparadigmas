"""
scenario_runner.py - Ejecutor de fases de calentamiento y medición de escenarios de benchmark.
Ejecuta POST /api/benchmark/run, valida checksums esperados, registra tiempos de pared,
nanosegundos del pipeline, métricas de JVM y detecta fallos/timeouts.
"""

from pathlib import Path
import time
from typing import Any, Dict, List, Optional
import httpx

from benchmark.bench.memory_sampler import MemorySampler

GOLDEN_CHECKSUMS = {
    "T": "bf5c6284888610a729ceeebf66b32d45854018ff61f0cf1867f67fba67d2a91e",
    "S": "a47b9117ce5d4a2ebf2b53a9d315435efcd58da1b54027647e7c83ea2c1941a9",
    "M": "0d2258e413ea3166ae104f79c4f6ec51ef6bd1595a8c0ed27bcdc97aa4ff58c4",
    "L": "888c0e28d9b7f93fbc48994aa3ac4f466761c16794b34d00e9edc1b02ac5c6b5",
    "XL": "fda8d3a8a73432d78b1cd451ab10fa36cf13902e8f235d1f5f851590f32ad475",
}


class ScenarioRunner:
    """Ejecutor de escenarios de benchmark sobre el backend HTTP."""

    def __init__(
        self,
        base_url: str,
        token: str,
        memory_sampler: Optional[MemorySampler] = None,
        timeout_seconds: float = 300.0,
    ):
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.memory_sampler = memory_sampler
        self.timeout_seconds = timeout_seconds

    def _get_headers(self) -> Dict[str, str]:
        return {
            "Authorization": f"Bearer {self.token}",
            "Content-Type": "application/json",
        }

    def run_single(
        self, scenario: str, seed: int = 42, include_report: bool = False
    ) -> Dict[str, Any]:
        """
        Ejecuta una petición a POST /api/benchmark/run y mide tiempo de pared del cliente.
        """
        endpoint = f"{self.base_url}/api/benchmark/run"
        if include_report:
            endpoint += "?includeReport=true"

        body = {"scenario": scenario, "seed": seed}
        expected_cs = GOLDEN_CHECKSUMS.get(scenario)

        t_start = time.perf_counter()
        try:
            with httpx.Client(timeout=self.timeout_seconds) as client:
                resp = client.post(endpoint, json=body, headers=self._get_headers())
            wall_time = time.perf_counter() - t_start

            if resp.status_code != 200:
                return {
                    "scenario": scenario,
                    "seed": seed,
                    "success": False,
                    "status_code": resp.status_code,
                    "error": f"HTTP {resp.status_code}: {resp.text[:300]}",
                    "wall_time_seconds": wall_time,
                }

            data = resp.json().get("data", {})
            actual_cs = data.get("checksum")
            cs_match = actual_cs == expected_cs if expected_cs else True

            return {
                "scenario": scenario,
                "seed": seed,
                "success": cs_match,
                "status_code": 200,
                "checksum": actual_cs,
                "checksum_expected": expected_cs,
                "checksum_match": cs_match,
                "wall_time_seconds": wall_time,
                "processedRecords": data.get("processedRecords", 0),
                "timings": data.get("timings", {}),
                "jvm": data.get("jvm", {}),
                "counts": data.get("counts", {}),
                "error": None if cs_match else f"Checksum mismatch: {actual_cs} != {expected_cs}",
            }
        except Exception as ex:
            wall_time = time.perf_counter() - t_start
            return {
                "scenario": scenario,
                "seed": seed,
                "success": False,
                "status_code": 0,
                "error": str(ex),
                "wall_time_seconds": wall_time,
            }

    def warmup(self, quick: bool = False) -> List[Dict[str, Any]]:
        """
        Fase 5: Calentamiento de la JVM.
        5 llamadas con S y 2 con M (en modo quick: 2 con T y 1 con S).
        """
        if self.memory_sampler:
            self.memory_sampler.set_phase("warmup")

        results = []
        calls = [("T", 2), ("S", 1)] if quick else [("S", 5), ("M", 2)]
        for sc, count in calls:
            for _ in range(count):
                res = self.run_single(sc)
                results.append(res)
        return results

    def measure_scenarios(
        self,
        scenarios: Optional[List[str]] = None,
        repetitions_map: Optional[Dict[str, int]] = None,
        quick: bool = False,
    ) -> Dict[str, Any]:
        """
        Fase 6: Medición para cada escenario con sus repeticiones.
        """
        if scenarios is None:
            scenarios = ["T", "S"] if quick else ["S", "M", "L", "XL"]

        default_reps = (
            {"T": 2, "S": 3}
            if quick
            else {"S": 10, "M": 7, "L": 5, "XL": 3}
        )
        reps_map = repetitions_map or default_reps

        scenario_results: Dict[str, Any] = {}

        for sc in scenarios:
            phase_name = f"scenario_{sc}"
            if self.memory_sampler:
                self.memory_sampler.set_phase(phase_name)

            count = reps_map.get(sc, 3)
            runs: List[Dict[str, Any]] = []
            failed = False

            for rep_idx in range(count):
                res = self.run_single(sc)
                runs.append(res)
                if not res["success"]:
                    failed = True
                    break

            mem_stats = (
                self.memory_sampler.get_phase_stats(phase_name)
                if self.memory_sampler
                else {}
            )

            scenario_results[sc] = {
                "scenario": sc,
                "repetitions_planned": count,
                "repetitions_completed": len(runs),
                "failed": failed,
                "runs": runs,
                "memory_stats": mem_stats,
            }

            if failed:
                # Si falló un escenario, abortar los siguientes escenarios de mayor volumen
                break

        return scenario_results
