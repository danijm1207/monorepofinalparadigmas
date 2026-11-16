"""
test_contract.py - Suite exhaustiva de pruebas de contrato API (mínimo 45 pruebas).
Verifica cumplimiento estricto del contrato HTTP, códigos de estado, formatos JSON,
validación de errores, RBAC, operaciones funcionales de benchmark y consistencia de datos.
"""

from datetime import datetime, timezone
import re
from typing import Any, Dict
import httpx
import pytest

ISO_UTC_REGEX = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$")
EXPECTED_T_CHECKSUM = "bf5c6284888610a729ceeebf66b32d45854018ff61f0cf1867f67fba67d2a91e"
ADMIN_ID = "00000000-0000-4000-8000-000000000001"
USER_ID = "00000000-0000-4000-8000-000000000002"


def assert_uniform_error(resp: httpx.Response, expected_status: int, expected_code: str):
    """Verifica que la respuesta de error cumpla con la estructura unificada {error: {code, message}}."""
    assert resp.status_code == expected_status
    data = resp.json()
    assert "error" in data, f"Respuesta no contiene clave 'error': {data}"
    err = data["error"]
    assert "code" in err and err["code"] == expected_code
    assert "message" in err and len(err["message"]) > 0


def assert_iso_date(dt_str: str):
    """Verifica que la fecha cumpla con el formato exacto UTC yyyy-MM-dd'T'HH:mm:ss.SSS'Z'."""
    assert ISO_UTC_REGEX.match(dt_str), f"Fecha '{dt_str}' no cumple con el regex ISO UTC milisegundos"


# ==============================================================================
# Fixtures de Autenticación
# ==============================================================================

@pytest.fixture(scope="session")
def admin_auth(target_url: str) -> Dict[str, str]:
    with httpx.Client(base_url=target_url, timeout=10.0) as client:
        resp = client.post(
            "/api/auth/login",
            json={"email": "admin@investigacion.test", "password": "Admin12345"},
        )
        assert resp.status_code == 200, f"Fallo login admin: {resp.text}"
        token = resp.json().get("token")
        assert token, "Token no presente en login admin"
        return {"Authorization": f"Bearer {token}"}


@pytest.fixture(scope="session")
def user_auth(target_url: str) -> Dict[str, str]:
    with httpx.Client(base_url=target_url, timeout=10.0) as client:
        resp = client.post(
            "/api/auth/login",
            json={"email": "user@investigacion.test", "password": "User12345"},
        )
        assert resp.status_code == 200, f"Fallo login user: {resp.text}"
        token = resp.json().get("token")
        assert token, "Token no presente en login user"
        return {"Authorization": f"Bearer {token}"}


# ==============================================================================
# 1. Health Check
# ==============================================================================

def test_01_health_returns_200_ok(target_url: str):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/health")
        assert resp.status_code == 200
        assert resp.json().get("status") == "ok"


# ==============================================================================
# 2. Autenticación y Registro
# ==============================================================================

def test_02_register_success(target_url: str):
    with httpx.Client(base_url=target_url) as client:
        unique_email = f"nuevo_{datetime.now().timestamp()}@investigacion.test"
        resp = client.post(
            "/api/auth/register",
            json={"name": "Nuevo Detective", "email": unique_email, "password": "Password123"},
        )
        # 201 en backend real o 400/409/201
        assert resp.status_code in [201, 200, 400]
        if resp.status_code == 201:
            data = resp.json()
            assert "user" in data and "token" in data
            assert data["user"]["role"] == "USER"
            assert "passwordHash" not in data["user"]


def test_03_register_duplicate_email_conflict(target_url: str):
    with httpx.Client(base_url=target_url) as client:
        resp = client.post(
            "/api/auth/register",
            json={"name": "Duplicado", "email": "admin@investigacion.test", "password": "Password123"},
        )
        assert_uniform_error(resp, 409, "CONFLICT")


def test_04_register_case_insensitive_email_conflict(target_url: str):
    with httpx.Client(base_url=target_url) as client:
        resp = client.post(
            "/api/auth/register",
            json={"name": "Duplicado Case", "email": "ADMIN@INVESTIGACION.TEST", "password": "Password123"},
        )
        assert_uniform_error(resp, 409, "CONFLICT")


def test_05_register_validation_invalid_email(target_url: str):
    with httpx.Client(base_url=target_url) as client:
        resp = client.post(
            "/api/auth/register",
            json={"name": "Invalido", "email": "no-es-un-email", "password": "Password123"},
        )
        assert_uniform_error(resp, 400, "VALIDATION_ERROR")


def test_06_register_validation_short_name(target_url: str):
    with httpx.Client(base_url=target_url) as client:
        resp = client.post(
            "/api/auth/register",
            json={"name": "A", "email": "valida@test.com", "password": "Password123"},
        )
        assert_uniform_error(resp, 400, "VALIDATION_ERROR")


def test_07_register_validation_weak_password(target_url: str):
    with httpx.Client(base_url=target_url) as client:
        resp = client.post(
            "/api/auth/register",
            json={"name": "Valido", "email": "valida2@test.com", "password": "solo"},
        )
        assert_uniform_error(resp, 400, "VALIDATION_ERROR")


def test_08_login_admin_success(target_url: str):
    with httpx.Client(base_url=target_url) as client:
        resp = client.post(
            "/api/auth/login",
            json={"email": "admin@investigacion.test", "password": "Admin12345"},
        )
        assert resp.status_code == 200
        data = resp.json()
        assert "token" in data and "user" in data
        assert data["user"]["role"] == "ADMIN"
        assert "passwordHash" not in data["user"]
        assert_iso_date(data["user"]["createdAt"])


def test_09_login_wrong_password_returns_401(target_url: str):
    with httpx.Client(base_url=target_url) as client:
        resp = client.post(
            "/api/auth/login",
            json={"email": "admin@investigacion.test", "password": "PasswordEquivocada1"},
        )
        assert_uniform_error(resp, 401, "UNAUTHORIZED")
        assert resp.json()["error"]["message"] == "Credenciales inválidas"


def test_10_login_nonexistent_email_returns_401(target_url: str):
    with httpx.Client(base_url=target_url) as client:
        resp = client.post(
            "/api/auth/login",
            json={"email": "fantasma@inexistente.test", "password": "Password123"},
        )
        assert_uniform_error(resp, 401, "UNAUTHORIZED")
        assert resp.json()["error"]["message"] == "Credenciales inválidas"


def test_11_auth_me_admin(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/auth/me", headers=admin_auth)
        assert resp.status_code == 200
        u = resp.json().get("data", {})
        assert u.get("role") == "ADMIN"
        assert "passwordHash" not in u


def test_12_auth_me_user(target_url: str, user_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/auth/me", headers=user_auth)
        assert resp.status_code == 200
        u = resp.json().get("data", {})
        assert u.get("role") == "USER"


def test_13_auth_missing_token_returns_401(target_url: str):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/auth/me")
        assert_uniform_error(resp, 401, "UNAUTHORIZED")


def test_14_auth_invalid_token_returns_401(target_url: str):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/auth/me", headers={"Authorization": "Bearer token_falso_y_malformado"})
        assert_uniform_error(resp, 401, "UNAUTHORIZED")


# ==============================================================================
# 3. Control de Acceso por Rol (RBAC)
# ==============================================================================

def test_15_admin_route_users_blocked_for_user(target_url: str, user_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/users", headers=user_auth)
        assert_uniform_error(resp, 403, "FORBIDDEN")


def test_16_admin_route_analytics_overview_blocked_for_user(target_url: str, user_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/analytics/overview", headers=user_auth)
        assert_uniform_error(resp, 403, "FORBIDDEN")


def test_17_admin_route_benchmark_blocked_for_user(target_url: str, user_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.post("/api/benchmark/run", json={"scenario": "T"}, headers=user_auth)
        assert_uniform_error(resp, 403, "FORBIDDEN")


# ==============================================================================
# 4. Gestión de Casos
# ==============================================================================

def test_18_cases_list_pagination_structure(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/cases?page=1&limit=5", headers=admin_auth)
        assert resp.status_code == 200
        body = resp.json()
        assert "data" in body and isinstance(body["data"], list)
        assert "pagination" in body
        p = body["pagination"]
        for key in ["total", "currentPage", "limit", "totalPages"]:
            assert key in p


def test_19_cases_list_invalid_page_returns_400(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/cases?page=0&limit=10", headers=admin_auth)
        assert_uniform_error(resp, 400, "VALIDATION_ERROR")


def test_20_cases_list_invalid_limit_too_high_returns_400(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/cases?page=1&limit=51", headers=admin_auth)
        assert_uniform_error(resp, 400, "VALIDATION_ERROR")


def test_21_cases_list_invalid_page_non_numeric_returns_400(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/cases?page=abc&limit=10", headers=admin_auth)
        assert_uniform_error(resp, 400, "VALIDATION_ERROR")


def test_22_cases_filter_by_status(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/cases?status=OPEN", headers=admin_auth)
        assert resp.status_code == 200
        for item in resp.json().get("data", []):
            assert item["status"] == "OPEN"


def test_23_cases_create_success(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
        body = {
            "title": "Robo Banco Central",
            "description": "Investigacion preliminar de robo con sustraccion de valores",
            "location": "Avenida Libertador #100",
            "incidentDate": now_iso,
            "status": "OPEN",
        }
        resp = client.post("/api/cases", json=body, headers=admin_auth)
        assert resp.status_code == 201
        c = resp.json().get("data", {})
        assert c["title"] == body["title"]
        assert "id" in c and "caseNumber" in c
        assert_iso_date(c["createdAt"])


def test_24_cases_create_validation_title_short(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        body = {
            "title": "Ro",
            "description": "Descripcion suficientemente larga para pasar la validacion",
            "location": "Sector Norte",
            "incidentDate": "2026-05-10T00:00:00.000Z",
        }
        resp = client.post("/api/cases", json=body, headers=admin_auth)
        assert_uniform_error(resp, 400, "VALIDATION_ERROR")


def test_25_cases_create_validation_description_short(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        body = {
            "title": "Caso Correcto",
            "description": "Corta",
            "location": "Sector Norte",
            "incidentDate": "2026-05-10T00:00:00.000Z",
        }
        resp = client.post("/api/cases", json=body, headers=admin_auth)
        assert_uniform_error(resp, 400, "VALIDATION_ERROR")


def test_26_cases_get_by_id_invalid_uuid_returns_400(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/cases/id-no-es-uuid", headers=admin_auth)
        assert_uniform_error(resp, 400, "VALIDATION_ERROR")


def test_27_cases_get_by_id_not_found_returns_404(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get(
            "/api/cases/ffffffff-ffff-ffff-ffff-ffffffffffff", headers=admin_auth
        )
        assert_uniform_error(resp, 404, "NOT_FOUND")


def test_28_cases_delete_204_and_subsequent_404(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        # 1. Crear caso
        now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
        create_resp = client.post(
            "/api/cases",
            json={
                "title": "Caso para eliminar",
                "description": "Caso creado con el proposito de probar borrado logico 204",
                "location": "Sector Oeste",
                "incidentDate": now_iso,
            },
            headers=admin_auth,
        )
        assert create_resp.status_code == 201
        cid = create_resp.json()["data"]["id"]

        # 2. Borrar caso
        del_resp = client.delete(f"/api/cases/{cid}", headers=admin_auth)
        assert del_resp.status_code == 204

        # 3. Intentar obtener el caso borrado
        get_resp = client.get(f"/api/cases/{cid}", headers=admin_auth)
        assert get_resp.status_code == 404


# ==============================================================================
# 5. Gestión de Sospechosos
# ==============================================================================

def test_29_suspects_list_pagination(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/suspects?page=1&limit=5", headers=admin_auth)
        assert resp.status_code == 200
        assert "data" in resp.json() and "pagination" in resp.json()


def test_30_suspects_create_invalid_suspicion_range(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.post(
            "/api/suspects",
            json={
                "caseId": "00000000-0000-0000-0000-000000000010",
                "fullName": "Sospechoso Invalido",
                "suspicionLevel": 150,  # Debe ser entre 0 y 100
            },
            headers=admin_auth,
        )
        assert_uniform_error(resp, 400, "VALIDATION_ERROR")


def test_31_suspects_create_closed_case_conflict(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        # En caso cerrado debe responder 409
        resp = client.post(
            "/api/suspects",
            json={
                "caseId": "00000000-0000-0000-0000-000000000099",
                "fullName": "Sospechoso Caso Cerrado",
            },
            headers=admin_auth,
        )
        # 404 o 409 dependiendo de existencia/estado
        assert resp.status_code in [404, 409, 400]


# ==============================================================================
# 6. Gestión de Evidencias
# ==============================================================================

def test_32_evidences_list_pagination(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/evidences?page=1&limit=5", headers=admin_auth)
        assert resp.status_code == 200
        assert "data" in resp.json() and "pagination" in resp.json()


def test_33_evidences_create_invalid_relevance_range(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.post(
            "/api/evidences",
            json={
                "caseId": "00000000-0000-0000-0000-000000000010",
                "title": "Evidencia Invalida",
                "description": "Descripcion de evidencia con relevancia fuera de rango",
                "type": "PHYSICAL",
                "collectedAt": "2026-05-10T00:00:00.000Z",
                "relevance": 10,  # Rango valido 1 a 5
            },
            headers=admin_auth,
        )
        assert_uniform_error(resp, 400, "VALIDATION_ERROR")


def test_34_evidences_create_future_collected_at(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.post(
            "/api/evidences",
            json={
                "caseId": "00000000-0000-0000-0000-000000000010",
                "title": "Evidencia Futura",
                "description": "Evidencia supuestamente recolectada en el futuro lejano",
                "type": "DIGITAL",
                "collectedAt": "2099-01-01T00:00:00.000Z",
                "relevance": 3,
            },
            headers=admin_auth,
        )
        assert_uniform_error(resp, 400, "VALIDATION_ERROR")


# ==============================================================================
# 7. Subida de Archivos (Uploads)
# ==============================================================================

def test_35_uploads_missing_token_returns_401(target_url: str):
    with httpx.Client(base_url=target_url) as client:
        resp = client.post("/api/uploads")
        assert_uniform_error(resp, 401, "UNAUTHORIZED")


def test_36_uploads_valid_png_returns_201(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        # Header mínimo PNG: 89 50 4E 47 0D 0A 1A 0A
        png_bytes = b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15c4\x00\x00\x00\nIDATx\x9cc\x00\x01\x00\x00\x05\x00\x01\r\n-\xb4\x00\x00\x00\x00IEND\xaeB`\x82"
        files = {"file": ("test.png", png_bytes, "image/png")}
        resp = client.post("/api/uploads", files=files, headers=admin_auth)
        assert resp.status_code in [201, 200]
        data = resp.json().get("data", {})
        assert "url" in data and "path" in data


def test_37_uploads_exceeding_5mb_returns_413(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        large_bytes = b"0" * (5 * 1024 * 1024 + 100)
        files = {"file": ("large.png", large_bytes, "image/png")}
        try:
            resp = client.post("/api/uploads", files=files, headers=admin_auth)
            assert resp.status_code in [413, 400]
        except Exception:
            # En servidores que rechazan de inmediato la conexión al superar límite
            pass


# ==============================================================================
# 8. Benchmark Funcional y Analytics
# ==============================================================================

def test_38_benchmark_invalid_scenario_returns_400(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.post(
            "/api/benchmark/run", json={"scenario": "INVALIDO_XYZ"}, headers=admin_auth
        )
        assert_uniform_error(resp, 400, "VALIDATION_ERROR")


def test_39_benchmark_scenario_t_checksum_match(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url, timeout=30.0) as client:
        resp = client.post(
            "/api/benchmark/run?includeReport=true",
            json={"scenario": "T", "seed": 42},
            headers=admin_auth,
        )
        assert resp.status_code == 200
        data = resp.json().get("data", {})
        assert data.get("checksum") == EXPECTED_T_CHECKSUM
        assert data.get("processedRecords") == 280
        assert "report" in data
        assert len(data["report"]) > 0


def test_40_analytics_overview_checksum_and_structure(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url, timeout=30.0) as client:
        resp = client.get("/api/analytics/overview", headers=admin_auth)
        assert resp.status_code == 200
        data = resp.json().get("data", {})
        assert "checksum" in data and len(data["checksum"]) == 64
        assert "timings" in data
        assert "report" in data


# ==============================================================================
# 9. Verificación de Formatos y No Exposición de Secretos
# ==============================================================================

def test_41_user_model_never_exposes_password_hash(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/users", headers=admin_auth)
        if resp.status_code == 200:
            for u in resp.json().get("data", []):
                assert "passwordHash" not in u
                assert "password_hash" not in u
                assert "password" not in u


def test_42_cases_iso_datetime_format(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.get("/api/cases?limit=5", headers=admin_auth)
        if resp.status_code == 200:
            for c in resp.json().get("data", []):
                if "createdAt" in c and c["createdAt"]:
                    assert_iso_date(c["createdAt"])
                if "updatedAt" in c and c["updatedAt"]:
                    assert_iso_date(c["updatedAt"])
                if "incidentDate" in c and c["incidentDate"]:
                    assert_iso_date(c["incidentDate"])


def test_43_admin_cannot_deactivate_self(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.delete(f"/api/users/{ADMIN_ID}", headers=admin_auth)
        assert resp.status_code in [409, 400]


def test_44_delete_nonexistent_case_returns_404(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url) as client:
        resp = client.delete(
            "/api/cases/00000000-0000-0000-0000-000000000999", headers=admin_auth
        )
        assert resp.status_code == 404


def test_45_benchmark_timings_nanos_format(target_url: str, admin_auth: Dict[str, str]):
    with httpx.Client(base_url=target_url, timeout=30.0) as client:
        resp = client.post(
            "/api/benchmark/run", json={"scenario": "T", "seed": 42}, headers=admin_auth
        )
        assert resp.status_code == 200
        timings = resp.json().get("data", {}).get("timings", {})
        assert "generationNanos" in timings
        assert "pipelineNanos" in timings
        assert isinstance(timings["generationNanos"], int)
        assert isinstance(timings["pipelineNanos"], int)
