"""
overview_runner.py - Ejecución y verificación del endpoint GET /api/analytics/overview.
Ejecuta 3 llamadas, valida tiempos de carga y pipeline, y compara el checksum obtenido
con el checksum esperado calculado por la referencia sobre los datos cargados en la BD.
"""

import time
from typing import Any, Dict, List, Optional
import httpx

from benchmark.bench.dataset import compute_overview_expected
from benchmark.bench.memory_sampler import MemorySampler


class OverviewRunner:
    """Ejecutor de pruebas de analytics overview."""

    def __init__(
        self,
        base_url: str,
        token: str,
        memory_sampler: Optional[MemorySampler] = None,
        timeout_seconds: float = 60.0,
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

    def run_overview_calls(
        self, repetitions: int = 3, seed: int = 42
    ) -> Dict[str, Any]:
        """
        Fase 7: Ejecuta repetitions llamadas a GET /api/analytics/overview.
        """
        if self.memory_sampler:
            self.memory_sampler.set_phase("overview")

        expected_cs, expected_lines = compute_overview_expected(seed=seed)
        endpoint = f"{self.base_url}/api/analytics/overview"

        calls: List[Dict[str, Any]] = []
        all_matched = True

        for i in range(repetitions):
            t_start = time.perf_counter()
            try:
                with httpx.Client(timeout=self.timeout_seconds) as client:
                    resp = client.get(endpoint, headers=self._get_headers())
                wall_time = time.perf_counter() - t_start

                if resp.status_code != 200:
                    calls.append(
                        {
                            "call_index": i + 1,
                            "success": False,
                            "status_code": resp.status_code,
                            "error": f"HTTP {resp.status_code}: {resp.text[:200]}",
                            "wall_time_seconds": wall_time,
                        }
                    )
                    all_matched = False
                    continue

                data = resp.json().get("data", {})
                actual_cs = data.get("checksum")
                match = actual_cs == expected_cs

                if not match:
                    all_matched = False

                calls.append(
                    {
                        "call_index": i + 1,
                        "success": match,
                        "status_code": 200,
                        "checksum": actual_cs,
                        "checksum_expected": expected_cs,
                        "checksum_match": match,
                        "wall_time_seconds": wall_time,
                        "processedRecords": data.get("processedRecords", 0),
                        "timings": data.get("timings", {}),
                    }
                )
            except Exception as ex:
                wall_time = time.perf_counter() - t_start
                calls.append(
                    {
                        "call_index": i + 1,
                        "success": False,
                        "status_code": 0,
                        "error": str(ex),
                        "wall_time_seconds": wall_time,
                    }
                )
                all_matched = False

        mem_stats = (
            self.memory_sampler.get_phase_stats("overview")
            if self.memory_sampler
            else {}
        )

        return {
            "repetitions": repetitions,
            "expected_checksum": expected_cs,
            "all_matched": all_matched,
            "calls": calls,
            "memory_stats": mem_stats,
        }
