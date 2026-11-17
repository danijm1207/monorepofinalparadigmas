"""
fake_backend.py - Servidor HTTP simulado para pruebas locales del arnés de benchmark.
Implementa todos los endpoints de la especificación (health, auth, cases, suspects, evidences,
benchmark/run, analytics/overview, uploads) devolviendo los checksums exactos de la referencia.
"""

from datetime import datetime, timezone
import json
import re
import time
from typing import Any, Dict, List, Optional
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
import threading
from urllib.parse import parse_qs, urlparse
import uuid

from benchmark.bench.reference import generate, pipeline, checksum, SCENARIOS
from benchmark.bench.dataset import compute_overview_expected

ADMIN_ID = "00000000-0000-4000-8000-000000000001"
USER_ID = "00000000-0000-4000-8000-000000000002"
UUID_REGEX = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)


class FakeBackendState:
    """Estado en memoria compartido por los handlers del servidor falso."""

    def __init__(self):
        self.cases: Dict[str, Dict[str, Any]] = {}
        self.suspects: Dict[str, Dict[str, Any]] = {}
        self.evidences: Dict[str, Dict[str, Any]] = {}
        self.users: Dict[str, Dict[str, Any]] = {
            ADMIN_ID: {
                "id": ADMIN_ID,
                "name": "Administrador",
                "email": "admin@investigacion.test",
                "role": "ADMIN",
                "state": "ACTIVE",
                "createdAt": "2026-10-01T00:00:00.000Z",
            },
            USER_ID: {
                "id": USER_ID,
                "name": "Investigador",
                "email": "user@investigacion.test",
                "role": "USER",
                "state": "ACTIVE",
                "createdAt": "2026-10-01T00:00:00.000Z",
            },
        }
        self.case_counter = 100
        self._seed_initial_cases()

    def _seed_initial_cases(self):
        cid = "00000000-0000-0000-0000-000000000010"
        self.cases[cid] = {
            "id": cid,
            "caseNumber": 1,
            "title": "Caso de Prueba Inicial",
            "description": "Investigacion para pruebas de contrato del backend",
            "location": "Distrito 1",
            "incidentDate": "2026-05-10T14:30:00.000Z",
            "status": "OPEN",
            "imageUrl": None,
            "ownerId": ADMIN_ID,
            "state": "ACTIVE",
            "createdAt": "2026-05-10T15:00:00.000Z",
            "updatedAt": "2026-05-10T15:00:00.000Z",
        }
        sid = "00000000-0000-0000-0000-000000000020"
        self.suspects[sid] = {
            "id": sid,
            "caseId": cid,
            "fullName": "Sospechoso de Prueba",
            "alias": "El Fantasma",
            "description": "Sujeto de interés en la investigación",
            "suspicionLevel": 75,
            "status": "PERSON_OF_INTEREST",
            "photoUrl": None,
            "state": "ACTIVE",
            "createdAt": "2026-05-10T15:10:00.000Z",
            "updatedAt": "2026-05-10T15:10:00.000Z",
        }
        eid = "00000000-0000-0000-0000-000000000030"
        self.evidences[eid] = {
            "id": eid,
            "caseId": cid,
            "suspectId": sid,
            "title": "Huellas Dactilares",
            "description": "Muestra dactiloscopica recolectada en la escena",
            "type": "FORENSIC",
            "collectedAt": "2026-05-10T15:30:00.000Z",
            "relevance": 5,
            "imageUrl": None,
            "state": "ACTIVE",
            "createdAt": "2026-05-10T15:35:00.000Z",
            "updatedAt": "2026-05-10T15:35:00.000Z",
        }


GLOBAL_STATE = FakeBackendState()


class FakeBackendHandler(BaseHTTPRequestHandler):
    """Manejador HTTP para el backend simulado."""

    def log_message(self, format, *args):
        # Silenciar logs en consola durante las pruebas
        return

    def _send_json(self, status: int, data: Any):
        body = json.dumps(data).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.send_header("Access-Control-Allow-Methods", "*")
        self.end_headers()
        self.wfile.write(body)

    def _send_error(
        self,
        status: int,
        code: str,
        message: str,
        details: Optional[List[Dict[str, str]]] = None,
    ):
        err_body: Dict[str, Any] = {"error": {"code": code, "message": message}}
        if details:
            err_body["error"]["details"] = details
        self._send_json(status, err_body)

    def _check_auth(self) -> Optional[Dict[str, Any]]:
        auth = self.headers.get("Authorization", "")
        if not auth.startswith("Bearer "):
            self._send_error(401, "UNAUTHORIZED", "Token ausente")
            return None
        token = auth.split(" ")[1]
        if token.startswith("admin") or token in ["admin_token_jwt", "admin_token_jwt_super_valid", "admin_token"]:
            return GLOBAL_STATE.users[ADMIN_ID]
        elif token == "user_token":
            return GLOBAL_STATE.users[USER_ID]
        elif token.startswith("user_"):
            return {
                "id": str(uuid.uuid4()),
                "name": "Usuario",
                "email": "user@test.com",
                "role": "USER",
                "createdAt": "2026-10-01T00:00:00.000Z",
            }
        else:
            self._send_error(401, "UNAUTHORIZED", "Token inválido o expirado")
            return None

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.send_header("Access-Control-Allow-Methods", "*")
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        qs = parse_qs(parsed.query)

        # 1. Health
        if path == "/api/health":
            self._send_json(200, {"status": "ok"})
            return

        # 2. Auth me
        if path == "/api/auth/me":
            user = self._check_auth()
            if not user:
                return
            self._send_json(200, {"data": user})
            return

        # 3. Analytics Overview
        if path == "/api/analytics/overview":
            user = self._check_auth()
            if not user:
                return
            if user.get("role") != "ADMIN":
                self._send_error(403, "FORBIDDEN", "Solo administradores pueden acceder")
                return

            cs, lines = compute_overview_expected()
            self._send_json(
                200,
                {
                    "data": {
                        "processedRecords": 14000,
                        "timings": {
                            "loadNanos": 2500000,
                            "pipelineNanos": 8500000,
                        },
                        "checksum": cs,
                        "report": lines,
                    }
                },
            )
            return

        # 4. Cases list
        if path == "/api/cases":
            user = self._check_auth()
            if not user:
                return

            page_str = qs.get("page", ["1"])[0]
            limit_str = qs.get("limit", ["10"])[0]
            try:
                page = int(page_str)
                limit = int(limit_str)
                if page < 1 or limit < 1 or limit > 50:
                    self._send_error(
                        400, "VALIDATION_ERROR", "Parámetros de paginación inválidos"
                    )
                    return
            except ValueError:
                self._send_error(
                    400, "VALIDATION_ERROR", "Parámetros de paginación no numéricos"
                )
                return

            active_cases = [
                c for c in GLOBAL_STATE.cases.values() if c.get("state") == "ACTIVE"
            ]
            status_filter = qs.get("status", [None])[0]
            if status_filter:
                active_cases = [c for c in active_cases if c.get("status") == status_filter]

            total = len(active_cases)
            totalPages = (total + limit - 1) // limit if total > 0 else 1

            self._send_json(
                200,
                {
                    "data": active_cases[:limit],
                    "pagination": {
                        "total": total,
                        "currentPage": page,
                        "limit": limit,
                        "totalPages": totalPages,
                    },
                },
            )
            return

        # 5. Case by ID
        m_case = re.match(r"^/api/cases/([^/]+)$", path)
        if m_case:
            user = self._check_auth()
            if not user:
                return
            cid = m_case.group(1)
            if not UUID_REGEX.match(cid):
                self._send_error(400, "VALIDATION_ERROR", "El ID no es un UUID válido")
                return
            case = GLOBAL_STATE.cases.get(cid)
            if not case or case.get("state") != "ACTIVE":
                self._send_error(404, "NOT_FOUND", "Caso no encontrado")
                return
            self._send_json(200, {"data": case})
            return

        # 6. Suspects list
        if path == "/api/suspects":
            user = self._check_auth()
            if not user:
                return
            active_s = [
                s for s in GLOBAL_STATE.suspects.values() if s.get("state") == "ACTIVE"
            ]
            self._send_json(
                200,
                {
                    "data": active_s,
                    "pagination": {
                        "total": len(active_s),
                        "currentPage": 1,
                        "limit": 10,
                        "totalPages": 1,
                    },
                },
            )
            return

        # 7. Evidences list
        if path == "/api/evidences":
            user = self._check_auth()
            if not user:
                return
            active_e = [
                e for e in GLOBAL_STATE.evidences.values() if e.get("state") == "ACTIVE"
            ]
            self._send_json(
                200,
                {
                    "data": active_e,
                    "pagination": {
                        "total": len(active_e),
                        "currentPage": 1,
                        "limit": 10,
                        "totalPages": 1,
                    },
                },
            )
            return

        # 8. Users list (ADMIN only)
        if path == "/api/users":
            user = self._check_auth()
            if not user:
                return
            if user.get("role") != "ADMIN":
                self._send_error(403, "FORBIDDEN", "Acceso denegado")
                return
            user_list = [
                u for u in GLOBAL_STATE.users.values() if u.get("state") == "ACTIVE"
            ]
            self._send_json(
                200,
                {
                    "data": user_list,
                    "pagination": {
                        "total": len(user_list),
                        "currentPage": 1,
                        "limit": 10,
                        "totalPages": 1,
                    },
                },
            )
            return

        self._send_error(404, "NOT_FOUND", "Ruta no encontrada")

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path
        length = int(self.headers.get("Content-Length", 0))
        body_bytes = self.rfile.read(length) if length > 0 else b"{}"

        # 1. Login
        if path == "/api/auth/login":
            try:
                body = json.loads(body_bytes.decode("utf-8"))
            except Exception:
                self._send_error(400, "VALIDATION_ERROR", "Cuerpo JSON inválido")
                return

            email = body.get("email", "").lower().strip()
            password = body.get("password", "")

            if email == "admin@investigacion.test" and password == "Admin12345":
                self._send_json(
                    200,
                    {
                        "user": GLOBAL_STATE.users[ADMIN_ID],
                        "token": "admin_token_jwt_super_valid",
                    },
                )
                return
            elif email == "user@investigacion.test" and password == "User12345":
                self._send_json(
                    200,
                    {
                        "user": GLOBAL_STATE.users[USER_ID],
                        "token": "user_token",
                    },
                )
                return
            else:
                self._send_error(401, "UNAUTHORIZED", "Credenciales inválidas")
                return

        # 2. Register
        if path == "/api/auth/register":
            try:
                body = json.loads(body_bytes.decode("utf-8"))
            except Exception:
                self._send_error(400, "VALIDATION_ERROR", "Cuerpo JSON inválido")
                return

            name = body.get("name", "")
            email = body.get("email", "").strip().lower()
            password = body.get("password", "")

            if len(name) < 2 or len(name) > 80:
                self._send_error(
                    400,
                    "VALIDATION_ERROR",
                    "Nombre inválido",
                    [{"field": "name", "message": "Debe tener entre 2 y 80 caracteres"}],
                )
                return

            if "@" not in email or "." not in email:
                self._send_error(
                    400,
                    "VALIDATION_ERROR",
                    "Email inválido",
                    [{"field": "email", "message": "Formato de email inválido"}],
                )
                return

            if len(password) < 8 or not re.search(r"[A-Za-z]", password) or not re.search(r"\d", password):
                self._send_error(
                    400,
                    "VALIDATION_ERROR",
                    "Contraseña inválida",
                    [{"field": "password", "message": "Debe tener al menos 8 caracteres, letra y número"}],
                )
                return

            # Verificar duplicado (case insensitive)
            for u in GLOBAL_STATE.users.values():
                if u.get("email", "").lower() == email:
                    self._send_error(409, "CONFLICT", "El email ya está registrado")
                    return

            uid = str(uuid.uuid4())
            now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
            new_user = {
                "id": uid,
                "name": name,
                "email": email,
                "role": "USER",
                "state": "ACTIVE",
                "createdAt": now_iso,
            }
            GLOBAL_STATE.users[uid] = new_user
            self._send_json(201, {"user": new_user, "token": f"user_{uid}"})
            return

        # 3. Benchmark Run
        if path == "/api/benchmark/run":
            user = self._check_auth()
            if not user:
                return
            if user.get("role") != "ADMIN":
                self._send_error(
                    403, "FORBIDDEN", "Solo administradores pueden ejecutar benchmark"
                )
                return

            try:
                body = json.loads(body_bytes.decode("utf-8"))
            except Exception:
                self._send_error(400, "VALIDATION_ERROR", "Cuerpo JSON inválido")
                return

            sc = body.get("scenario")
            seed = body.get("seed", 42)
            if sc not in SCENARIOS:
                self._send_error(400, "VALIDATION_ERROR", f"Escenario '{sc}' no permitido")
                return

            qs = parse_qs(parsed.query)
            include_rep = qs.get("includeReport", ["false"])[0].lower() == "true"

            t0 = time.perf_counter_ns()
            cases, suspects, evid = generate(sc, seed)
            t1 = time.perf_counter_ns()
            lines = pipeline(cases, suspects, evid)
            t2 = time.perf_counter_ns()
            cs = checksum(lines)

            c_count = len(cases)
            s_count = len(suspects)
            e_count = len(evid)

            resp_data: Dict[str, Any] = {
                "scenario": sc,
                "seed": seed,
                "counts": {
                    "cases": c_count,
                    "suspects": s_count,
                    "evidences": e_count,
                },
                "processedRecords": c_count + s_count + e_count,
                "timings": {
                    "generationNanos": t1 - t0,
                    "pipelineNanos": t2 - t1,
                },
                "jvm": {
                    "heapUsedBytes": 128 * 1024 * 1024,
                    "heapMaxBytes": 1400 * 1024 * 1024,
                },
                "checksum": cs,
            }
            if include_rep:
                resp_data["report"] = lines

            self._send_json(200, {"data": resp_data})
            return

        # 4. Create Case
        if path == "/api/cases":
            user = self._check_auth()
            if not user:
                return

            try:
                body = json.loads(body_bytes.decode("utf-8"))
            except Exception:
                self._send_error(400, "VALIDATION_ERROR", "Cuerpo JSON inválido")
                return

            title = body.get("title", "")
            desc = body.get("description", "")
            if len(title) < 3 or len(title) > 120:
                self._send_error(
                    400,
                    "VALIDATION_ERROR",
                    "Error de validación",
                    [{"field": "title", "message": "El título debe tener entre 3 y 120 caracteres"}],
                )
                return

            if len(desc) < 10 or len(desc) > 2000:
                self._send_error(
                    400,
                    "VALIDATION_ERROR",
                    "Error de validación",
                    [{"field": "description", "message": "La descripción debe tener entre 10 y 2000 caracteres"}],
                )
                return

            GLOBAL_STATE.case_counter += 1
            cid = str(uuid.uuid4())
            now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
            new_case = {
                "id": cid,
                "caseNumber": GLOBAL_STATE.case_counter,
                "title": title,
                "description": desc,
                "location": body.get("location", "Ubicación Central"),
                "incidentDate": body.get("incidentDate", now_iso),
                "status": body.get("status", "OPEN"),
                "imageUrl": body.get("imageUrl"),
                "ownerId": user["id"],
                "state": "ACTIVE",
                "createdAt": now_iso,
                "updatedAt": now_iso,
            }
            GLOBAL_STATE.cases[cid] = new_case
            self._send_json(201, {"data": new_case})
            return

        # 5. Create Suspect
        if path == "/api/suspects":
            user = self._check_auth()
            if not user:
                return

            try:
                body = json.loads(body_bytes.decode("utf-8"))
            except Exception:
                self._send_error(400, "VALIDATION_ERROR", "Cuerpo JSON inválido")
                return

            cid = body.get("caseId")
            susp = body.get("suspicionLevel", 0)
            if susp < 0 or susp > 100:
                self._send_error(
                    400, "VALIDATION_ERROR", "Nivel de sospecha debe estar entre 0 y 100"
                )
                return

            if cid not in GLOBAL_STATE.cases:
                self._send_error(404, "NOT_FOUND", "Caso asociado no encontrado")
                return

            if GLOBAL_STATE.cases[cid].get("status") == "CLOSED":
                self._send_error(409, "CONFLICT", "No se pueden agregar sospechosos a casos cerrados")
                return

            sid = str(uuid.uuid4())
            now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
            new_suspect = {
                "id": sid,
                "caseId": cid,
                "fullName": body.get("fullName", "Sospechoso"),
                "alias": body.get("alias"),
                "description": body.get("description"),
                "suspicionLevel": susp,
                "status": body.get("status", "PERSON_OF_INTEREST"),
                "photoUrl": body.get("photoUrl"),
                "state": "ACTIVE",
                "createdAt": now_iso,
                "updatedAt": now_iso,
            }
            GLOBAL_STATE.suspects[sid] = new_suspect
            self._send_json(201, {"data": new_suspect})
            return

        # 6. Create Evidence
        if path == "/api/evidences":
            user = self._check_auth()
            if not user:
                return

            try:
                body = json.loads(body_bytes.decode("utf-8"))
            except Exception:
                self._send_error(400, "VALIDATION_ERROR", "Cuerpo JSON inválido")
                return

            rel = body.get("relevance", 3)
            if rel < 1 or rel > 5:
                self._send_error(
                    400, "VALIDATION_ERROR", "Relevancia debe estar entre 1 y 5"
                )
                return

            coll = body.get("collectedAt", "")
            if coll > "2090":
                self._send_error(
                    400, "VALIDATION_ERROR", "La fecha de recolección no puede ser futura"
                )
                return

            eid = str(uuid.uuid4())
            now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
            new_evid = {
                "id": eid,
                "caseId": body.get("caseId"),
                "suspectId": body.get("suspectId"),
                "title": body.get("title", "Evidencia"),
                "description": body.get("description", "Descripción"),
                "type": body.get("type", "PHYSICAL"),
                "collectedAt": coll or now_iso,
                "relevance": rel,
                "imageUrl": body.get("imageUrl"),
                "state": "ACTIVE",
                "createdAt": now_iso,
                "updatedAt": now_iso,
            }
            GLOBAL_STATE.evidences[eid] = new_evid
            self._send_json(201, {"data": new_evid})
            return

        # 7. Uploads
        if path == "/api/uploads":
            user = self._check_auth()
            if not user:
                return

            if length > 5 * 1024 * 1024:
                self._send_error(413, "PAYLOAD_TOO_LARGE", "El archivo excede el límite de 5 MB")
                return

            self._send_json(
                201,
                {
                    "data": {
                        "url": "http://localhost:8080/uploads/cases/00000000-0000-4000-8000-000000000099.png",
                        "path": "cases/00000000-0000-4000-8000-000000000099.png",
                    }
                },
            )
            return

        self._send_error(404, "NOT_FOUND", "Ruta no encontrada")

    def do_DELETE(self):
        parsed = urlparse(self.path)
        path = parsed.path

        # Delete Case
        m_case = re.match(r"^/api/cases/([^/]+)$", path)
        if m_case:
            user = self._check_auth()
            if not user:
                return
            cid = m_case.group(1)
            if cid in GLOBAL_STATE.cases:
                GLOBAL_STATE.cases[cid]["state"] = "INACTIVE"
                self.send_response(204)
                self.end_headers()
                return
            self._send_error(404, "NOT_FOUND", "Caso no encontrado")
            return

        # Delete User (ADMIN only)
        m_user = re.match(r"^/api/users/([^/]+)$", path)
        if m_user:
            user = self._check_auth()
            if not user:
                return
            if user.get("role") != "ADMIN":
                self._send_error(403, "FORBIDDEN", "Acceso denegado")
                return
            uid = m_user.group(1)
            if uid == ADMIN_ID:
                self._send_error(
                    409, "CONFLICT", "El administrador no puede desactivarse a sí mismo"
                )
                return
            if uid in GLOBAL_STATE.users:
                GLOBAL_STATE.users[uid]["state"] = "INACTIVE"
                self.send_response(204)
                self.end_headers()
                return
            self._send_error(404, "NOT_FOUND", "Usuario no encontrado")
            return

        self._send_error(404, "NOT_FOUND", "Ruta no encontrada")


class FakeBackendServer:
    """Wrapper para arrancar y detener el servidor falso en un hilo."""

    def __init__(self, port: int = 8080):
        self.port = port
        self.server: Optional[HTTPServer] = None
        self.thread: Optional[threading.Thread] = None

    def start(self):
        self.server = ThreadingHTTPServer(("127.0.0.1", self.port), FakeBackendHandler)
        self.server.daemon_threads = True
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def stop(self):
        if self.server:
            self.server.shutdown()
            self.server.server_close()
        if self.thread:
            self.thread.join(timeout=2)


if __name__ == "__main__":
    srv = FakeBackendServer(port=8080)
    print("Iniciando fake_backend en http://127.0.0.1:8080 ...")
    srv.start()
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("Deteniendo fake_backend.")
        srv.stop()
