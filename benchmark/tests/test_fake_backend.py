"""
test_fake_backend.py - Pruebas del servidor falso para validar que sirve correctamente las rutas.
"""

import time
import httpx
import pytest

from benchmark.tests.fake_backend import FakeBackendServer


@pytest.fixture(scope="module")
def fake_server():
    srv = FakeBackendServer(port=8888)
    srv.start()
    time.sleep(0.3)
    yield "http://127.0.0.1:8888"
    srv.stop()


def test_fake_backend_health(fake_server: str):
    with httpx.Client(base_url=fake_server) as client:
        resp = client.get("/api/health")
        assert resp.status_code == 200
        assert resp.json()["status"] == "ok"


def test_fake_backend_benchmark_run_t(fake_server: str):
    with httpx.Client(base_url=fake_server) as client:
        headers = {"Authorization": "Bearer admin_token_valid"}
        resp = client.post(
            "/api/benchmark/run?includeReport=true",
            json={"scenario": "T", "seed": 42},
            headers=headers,
        )
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert (
            data["checksum"]
            == "bf5c6284888610a729ceeebf66b32d45854018ff61f0cf1867f67fba67d2a91e"
        )
        assert "report" in data
        assert len(data["report"]) > 0


def test_fake_backend_overview(fake_server: str):
    with httpx.Client(base_url=fake_server) as client:
        headers = {"Authorization": "Bearer admin_token_valid"}
        resp = client.get("/api/analytics/overview", headers=headers)
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert len(data["checksum"]) == 64
        assert "report" in data
