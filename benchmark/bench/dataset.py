"""
dataset.py - Generación y carga determinista del conjunto de datos en PostgreSQL.
Genera el escenario S con UUIDv5 deterministas, 5% de evidencias inactivas, dueño admin,
y realiza la carga masiva mediante COPY para las pruebas de carga y el endpoint overview.
"""

from datetime import date, timedelta
import io
from pathlib import Path
from typing import Dict, List, Optional, Tuple
import uuid

from benchmark.bench.reference import generate, pipeline, checksum

# Namespace determinista para UUID v5
DATASET_NAMESPACE = uuid.UUID("00000000-0000-4000-8000-000000000000")
ADMIN_OWNER_ID = "00000000-0000-4000-8000-000000000001"


def get_case_uuid(cid: str) -> str:
    """Genera UUID v5 determinista para un caso a partir de su ID canónico (e.g. C0000000)."""
    return str(uuid.uuid5(DATASET_NAMESPACE, f"case:{cid}"))


def get_suspect_uuid(sid: str) -> str:
    """Genera UUID v5 determinista para un sospechoso (e.g. S0000000)."""
    return str(uuid.uuid5(DATASET_NAMESPACE, f"suspect:{sid}"))


def get_evidence_uuid(eid: int) -> str:
    """Genera UUID v5 determinista para una evidencia."""
    return str(uuid.uuid5(DATASET_NAMESPACE, f"evidence:{eid:08d}"))


class DatasetBundle:
    """Contenedor de datos generados en memoria y formateados para base de datos."""

    def __init__(
        self,
        cases_raw: List[Tuple[str, str, int]],
        suspects_raw: List[Tuple[str, str, int, str]],
        evid_raw: List[Tuple[str, Optional[str], str, int, int, bool]],
    ):
        self.cases_raw = cases_raw
        self.suspects_raw = suspects_raw
        self.evid_raw = evid_raw

        # Mapeos de ID canónico a UUID
        self.case_id_to_uuid: Dict[str, str] = {
            c[0]: get_case_uuid(c[0]) for c in cases_raw
        }
        self.suspect_id_to_uuid: Dict[str, str] = {
            s[0]: get_suspect_uuid(s[0]) for s in suspects_raw
        }

    def generate_cases_csv(self) -> str:
        """
        Genera CSV formateado para COPY en la tabla cases:
        id, title, description, location, incident_date, status, image_url, state, owner_id
        """
        output = io.StringIO()
        for cid, status, epoch_day in self.cases_raw:
            case_uuid = self.case_id_to_uuid[cid]
            d = date(1970, 1, 1) + timedelta(days=epoch_day)
            dt_iso = f"{d.isoformat()}T00:00:00.000Z"
            title = f"Investigacion {cid}"
            desc = f"Expediente forense correspondiente al caso judicial {cid}"
            loc = "Distrito Metropolitano"
            output.write(
                f"{case_uuid}\t{title}\t{desc}\t{loc}\t{dt_iso}\t{status}\t\\N\tACTIVE\t{ADMIN_OWNER_ID}\n"
            )
        return output.getvalue()

    def generate_suspects_csv(self) -> str:
        """
        Genera CSV formateado para COPY en la tabla suspects:
        id, full_name, alias, description, suspicion_level, status, photo_url, state, case_id
        """
        output = io.StringIO()
        for sid, cid, suspicion, status in self.suspects_raw:
            suspect_uuid = self.suspect_id_to_uuid[sid]
            case_uuid = self.case_id_to_uuid[cid]
            full_name = f"Sospechoso {sid}"
            alias = f"Alias_{sid}"
            desc = f"Perfil del sospechoso {sid} vinculado a {cid}"
            output.write(
                f"{suspect_uuid}\t{full_name}\t{alias}\t{desc}\t{suspicion}\t{status}\t\\N\tACTIVE\t{case_uuid}\n"
            )
        return output.getvalue()

    def generate_evidences_csv(self) -> str:
        """
        Genera CSV formateado para COPY en la tabla evidences:
        id, title, description, type, collected_at, relevance, image_url, state, case_id, suspect_id
        """
        output = io.StringIO()
        for idx, (cid, sid, ev_type, rel, epoch_day, active) in enumerate(
            self.evid_raw
        ):
            ev_uuid = get_evidence_uuid(idx)
            case_uuid = self.case_id_to_uuid[cid]
            suspect_uuid = self.suspect_id_to_uuid[sid] if sid else "\\N"
            d = date(1970, 1, 1) + timedelta(days=epoch_day)
            dt_iso = f"{d.isoformat()}T00:00:00.000Z"
            state = "ACTIVE" if active else "INACTIVE"
            title = f"Pieza Evidencia #{idx:06d}"
            desc = f"Registro de peritaje forense para evidencia {idx} tipo {ev_type}"
            output.write(
                f"{ev_uuid}\t{title}\t{desc}\t{ev_type}\t{dt_iso}\t{rel}\t\\N\t{state}\t{case_uuid}\t{suspect_uuid}\n"
            )
        return output.getvalue()


def build_scenario_s_dataset(seed: int = 42) -> DatasetBundle:
    """Construye el conjunto de datos determinista del escenario S."""
    cases, suspects, evid = generate("S", seed=seed)
    return DatasetBundle(cases, suspects, evid)


def compute_overview_expected(seed: int = 42) -> Tuple[str, List[str]]:
    """
    Calcula el checksum SHA-256 y líneas canónicas esperadas para el endpoint GET /api/analytics/overview.
    Dado que overview lee solo registros con state ACTIVE de la BD, el resultado es idéntico a ejecutar
    el pipeline sobre los registros con active=True (95% de evidencias y 100% de casos/sospechosos).
    """
    cases, suspects, evid = generate("S", seed=seed)
    # En la BD solo están ACTIVE las evidencias con active=True
    active_evid = [e for e in evid if e[5]]
    lines = pipeline(cases, suspects, active_evid)
    cs = checksum(lines)
    return cs, lines


def load_dataset_into_postgres(
    ds: DatasetBundle,
    host: str = "localhost",
    port: int = 55432,
    dbname: str = "investigacion",
    user: str = "postgres",
    password: str = "postgres_dev_password_change_me",
) -> None:
    """Carga masivamente los datos en PostgreSQL utilizando psycopg y COPY."""
    import psycopg

    conn_str = f"host={host} port={port} dbname={dbname} user={user} password={password}"
    with psycopg.connect(conn_str) as conn:
        with conn.cursor() as cur:
            # Limpiar tablas en orden inverso de dependencias (manteniendo users intacto)
            cur.execute("TRUNCATE TABLE evidences, suspects, cases CASCADE;")

            # COPY cases
            cases_tsv = ds.generate_cases_csv()
            with cur.copy(
                "COPY cases (id, title, description, location, incident_date, status, image_url, state, owner_id) FROM STDIN"
            ) as copy:
                copy.write(cases_tsv)

            # COPY suspects
            suspects_tsv = ds.generate_suspects_csv()
            with cur.copy(
                "COPY suspects (id, full_name, alias, description, suspicion_level, status, photo_url, state, case_id) FROM STDIN"
            ) as copy:
                copy.write(suspects_tsv)

            # COPY evidences
            evid_tsv = ds.generate_evidences_csv()
            with cur.copy(
                "COPY evidences (id, title, description, type, collected_at, relevance, image_url, state, case_id, suspect_id) FROM STDIN"
            ) as copy:
                copy.write(evid_tsv)

        conn.commit()
