"""
conftest.py - Configuración y fixtures globales para pytest en el arnés de benchmark.
"""

import pytest


def pytest_addoption(parser):
    parser.addoption(
        "--target",
        action="store",
        default="http://127.0.0.1:8080",
        help="URL base del backend a probar (ejemplo: http://localhost:8081)",
    )


@pytest.fixture(scope="session")
def target_url(request):
    return request.config.getoption("--target").rstrip("/")
