"""
http_load.py - Generador asíncrono de carga HTTP con mezcla representativa de endpoints.
Ejecuta niveles de concurrencia 1, 10 y 50 con calentamiento y medición,
calculando RPS, latencias p50, p95, p99 y tasa de errores inesperados.
"""

import asyncio
from datetime import datetime, timezone
import random
import time
from typing import Any, Dict, List, Optional
import httpx

from benchmark.bench.memory_sampler import MemorySampler


class HttpLoadTester:
    """Generador de carga HTTP asíncrono para evaluación de rendimiento."""

    def __init__(
        self,
        base_url: str,
        admin_token: str,
        user_email: str = "user@investigacion.test",
        user_password: str = "User12345",
        memory_sampler: Optional[MemorySampler] = None,
    ):
        self.base_url = base_url.rstrip("/")
        self.admin_token = admin_token
        self.user_email = user_email
        self.user_password = user_password
        self.memory_sampler = memory_sampler
        self.known_case_ids: List[str] = []

    async def initialize(self):
        """Descubre IDs de casos reales en la BD para usarlos en peticiones individuales."""
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                headers = {"Authorization": f"Bearer {self.admin_token}"}
                resp = await client.get(
                    f"{self.base_url}/api/cases?page=1&limit=50", headers=headers
                )
                if resp.status_code == 200:
                    items = resp.json().get("data", [])
                    self.known_case_ids = [item["id"] for item in items if "id" in item]
        except Exception:
            pass

        if not self.known_case_ids:
            # Fallback a UUIDs sintéticos deterministas
            from benchmark.bench.dataset import get_case_uuid
            self.known_case_ids = [get_case_uuid(f"C{i:07d}") for i in range(50)]

    async def _send_mixed_request(
        self, client: httpx.AsyncClient, req_id: int
    ) -> Dict[str, Any]:
        """
        Ejecuta una petición de acuerdo a la mezcla proporcional:
        - 40% GET /api/cases (alternando filtros q / status)
        - 20% GET /api/cases/{id}
        - 15% GET /api/suspects?caseId={id}
        - 10% GET /api/evidences?caseId={id}
        - 10% POST /api/cases
        - 5% POST /api/auth/login
        """
        roll = random.randint(0, 99)
        case_id = random.choice(self.known_case_ids)
        headers = {"Authorization": f"Bearer {self.admin_token}"}

        t_start = time.perf_counter()
        endpoint_name = ""
        try:
            if roll < 40:
                endpoint_name = "GET_cases_list"
                sub = roll % 3
                if sub == 0:
                    url = f"{self.base_url}/api/cases?page=1&limit=10"
                elif sub == 1:
                    url = f"{self.base_url}/api/cases?page=1&limit=10&q=Investigacion"
                else:
                    url = f"{self.base_url}/api/cases?page=1&limit=10&status=OPEN"
                resp = await client.get(url, headers=headers)

            elif roll < 60:
                endpoint_name = "GET_case_by_id"
                url = f"{self.base_url}/api/cases/{case_id}"
                resp = await client.get(url, headers=headers)

            elif roll < 75:
                endpoint_name = "GET_suspects_by_case"
                url = f"{self.base_url}/api/suspects?caseId={case_id}"
                resp = await client.get(url, headers=headers)

            elif roll < 85:
                endpoint_name = "GET_evidences_by_case"
                url = f"{self.base_url}/api/evidences?caseId={case_id}"
                resp = await client.get(url, headers=headers)

            elif roll < 95:
                endpoint_name = "POST_case"
                url = f"{self.base_url}/api/cases"
                now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
                body = {
                    "title": f"Caso Carga {req_id}",
                    "description": "Caso sintético generado durante prueba de carga HTTP concurrente",
                    "location": "Sector Central Operativo",
                    "incidentDate": now_iso,
                    "status": "OPEN",
                }
                resp = await client.post(url, json=body, headers=headers)

            else:
                endpoint_name = "POST_login"
                url = f"{self.base_url}/api/auth/login"
                body = {"email": self.user_email, "password": self.user_password}
                resp = await client.post(url, json=body)

            latency_ms = (time.perf_counter() - t_start) * 1000.0
            status = resp.status_code
            is_error = status >= 500

            return {
                "endpoint": endpoint_name,
                "status_code": status,
                "latency_ms": latency_ms,
                "unexpected_error": is_error,
            }

        except Exception as ex:
            latency_ms = (time.perf_counter() - t_start) * 1000.0
            return {
                "endpoint": endpoint_name or "error",
                "status_code": 0,
                "latency_ms": latency_ms,
                "unexpected_error": True,
                "error": str(ex),
            }

    async def _worker(
        self,
        worker_id: int,
        stop_event: asyncio.Event,
        results_list: List[Dict[str, Any]],
        client: httpx.AsyncClient,
    ):
        req_counter = 0
        while not stop_event.is_set():
            req_counter += 1
            res = await self._send_mixed_request(client, worker_id * 100000 + req_counter)
            results_list.append(res)
            # Pequeña pausa opcional para evitar saturar el loop de eventos en Windows
            await asyncio.sleep(0.01)

    async def run_load_stage(
        self,
        concurrency: int,
        warmup_seconds: float,
        measure_seconds: float,
    ) -> Dict[str, Any]:
        """
        Ejecuta una etapa de carga con concurrencia fija:
        1. Calentamiento durante warmup_seconds (las peticiones se descartan).
        2. Medición durante measure_seconds (se calculan métricas).
        """
        phase_name = f"http_c{concurrency}"
        if self.memory_sampler:
            self.memory_sampler.set_phase(phase_name)

        limits = httpx.Limits(
            max_connections=concurrency * 2, max_keepalive_connections=concurrency
        )
        async with httpx.AsyncClient(limits=limits, timeout=10.0) as client:
            # 1. Warmup
            if warmup_seconds > 0:
                stop_warmup = asyncio.Event()
                warmup_results: List[Dict[str, Any]] = []
                tasks = [
                    asyncio.create_task(
                        self._worker(w_id, stop_warmup, warmup_results, client)
                    )
                    for w_id in range(concurrency)
                ]
                await asyncio.sleep(warmup_seconds)
                stop_warmup.set()
                await asyncio.gather(*tasks, return_exceptions=True)

            # 2. Medición
            stop_measure = asyncio.Event()
            measured_results: List[Dict[str, Any]] = []
            t_start = time.perf_counter()

            tasks = [
                asyncio.create_task(
                    self._worker(w_id, stop_measure, measured_results, client)
                )
                for w_id in range(concurrency)
            ]
            await asyncio.sleep(measure_seconds)
            stop_measure.set()
            await asyncio.gather(*tasks, return_exceptions=True)
            duration = time.perf_counter() - t_start

        # Calcular estadísticas
        total_requests = len(measured_results)
        rps = total_requests / duration if duration > 0 else 0.0

        latencies = sorted([r["latency_ms"] for r in measured_results])
        unexpected_errors = sum(1 for r in measured_results if r["unexpected_error"])

        def get_percentile(sorted_data: List[float], p: float) -> float:
            if not sorted_data:
                return 0.0
            k = (len(sorted_data) - 1) * (p / 100.0)
            f = int(k)
            c = min(f + 1, len(sorted_data) - 1)
            d0 = sorted_data[f] * (c - k)
            d1 = sorted_data[c] * (k - f)
            return d0 + d1

        p50 = get_percentile(latencies, 50.0)
        p95 = get_percentile(latencies, 95.0)
        p99 = get_percentile(latencies, 99.0)

        mem_stats = (
            self.memory_sampler.get_phase_stats(phase_name)
            if self.memory_sampler
            else {}
        )

        return {
            "concurrency": concurrency,
            "duration_seconds": duration,
            "total_requests": total_requests,
            "rps": rps,
            "p50_ms": p50,
            "p95_ms": p95,
            "p99_ms": p99,
            "unexpected_errors": unexpected_errors,
            "error_rate": (unexpected_errors / total_requests) if total_requests > 0 else 0.0,
            "memory_stats": mem_stats,
        }

    def run_all_concurrencies(
        self,
        concurrencies: Optional[List[int]] = None,
        warmup_seconds: float = 10.0,
        measure_seconds: float = 30.0,
    ) -> Dict[str, Any]:
        """Ejecuta secuencialmente todos los niveles de concurrencia requeridos."""
        if concurrencies is None:
            concurrencies = [1, 10, 50]

        async def _run():
            await self.initialize()
            stages = {}
            for c in concurrencies:
                stages[f"c{c}"] = await self.run_load_stage(
                    c, warmup_seconds, measure_seconds
                )
            return stages

        return asyncio.run(_run())
