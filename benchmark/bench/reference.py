"""
reference.py - Generador determinista y pipeline de referencia funcional.
Fuente única e inmutable de verdad para el benchmark.
"""

from datetime import date, timedelta
import hashlib
from typing import Dict, List, Optional, Tuple

TYPES = ["PHYSICAL", "DIGITAL", "DOCUMENT", "TESTIMONY", "FORENSIC"]
WEIGHT = {"PHYSICAL": 10, "DIGITAL": 12, "DOCUMENT": 8, "TESTIMONY": 6, "FORENSIC": 15}
CASE_STATUS = ["OPEN", "IN_PROGRESS", "CLOSED"]
SUSPECT_STATUS = ["PERSON_OF_INTEREST", "DETAINED", "CLEARED"]
BASE_DAY = (date(2023, 1, 1) - date(1970, 1, 1)).days
SCENARIOS = {
    "T": (20, 60, 200),
    "S": (1000, 3000, 10000),
    "M": (10000, 30000, 100000),
    "L": (50000, 150000, 500000),
    "XL": (100000, 300000, 1000000),
}
MIN_RELEVANCE = 2


class Rng:
    """Generador lineal congruencial de 32 bits idéntico a la especificación."""

    def __init__(self, seed: int):
        self.s = seed & 0xFFFFFFFF

    def next(self) -> int:
        self.s = (self.s * 1664525 + 1013904223) & 0xFFFFFFFF
        return self.s

    def next_int(self, n: int) -> int:
        return (self.next() >> 8) % n


def generate(
    scenario: str, seed: int = 42
) -> Tuple[
    List[Tuple[str, str, int]],
    List[Tuple[str, str, int, str]],
    List[Tuple[str, Optional[str], str, int, int, bool]],
]:
    """Genera datos deterministas para el escenario y semilla indicados."""
    c, s, e = SCENARIOS[scenario]
    r = Rng(seed)
    cases: List[Tuple[str, str, int]] = []
    for i in range(c):
        st = CASE_STATUS[r.next_int(3)]
        day = BASE_DAY + r.next_int(1096)
        cases.append(("C%07d" % i, st, day))
    suspects: List[Tuple[str, str, int, str]] = []
    for j in range(s):
        ci = r.next_int(c)
        sus = r.next_int(101)
        st = SUSPECT_STATUS[r.next_int(3)]
        suspects.append(("S%07d" % j, "C%07d" % ci, sus, st))
    evid: List[Tuple[str, Optional[str], str, int, int, bool]] = []
    for k in range(e):
        ci = r.next_int(c)
        h = r.next_int(100)
        si = r.next_int(s)
        t = TYPES[r.next_int(5)]
        rel = 1 + r.next_int(5)
        day = BASE_DAY + r.next_int(1096)
        a = r.next_int(100)
        evid.append(
            (
                "C%07d" % ci,
                ("S%07d" % si) if h < 60 else None,
                t,
                rel,
                day,
                a < 95,
            )
        )
    return cases, suspects, evid


def pipeline(
    cases: List[Tuple[str, str, int]],
    suspects: List[Tuple[str, str, int, str]],
    evid: List[Tuple[str, Optional[str], str, int, int, bool]],
) -> List[str]:
    """Ejecuta el pipeline analítico funcional de referencia."""
    valid = [x for x in evid if x[5] and x[3] >= MIN_RELEVANCE]
    scored = [(x, x[3] * WEIGHT[x[2]]) for x in valid]
    lines: List[str] = []
    total = sum(sc for _, sc in scored)
    mx = max((sc for _, sc in scored), default=0)
    by_case: Dict[str, Tuple[int, int]] = {}
    for x, sc in scored:
        cnt, tot = by_case.get(x[0], (0, 0))
        by_case[x[0]] = (cnt + 1, tot + sc)
    lines.append(
        "totals|valid=%d|score=%d|maxScore=%d|casesWithEvidence=%d"
        % (len(valid), total, mx, len(by_case))
    )
    for t in TYPES:
        sel = [sc for x, sc in scored if x[2] == t]
        lines.append("type|%s|count=%d|score=%d" % (t, len(sel), sum(sel)))
    months: Dict[int, int] = {}
    for x, _ in scored:
        d = date(1970, 1, 1) + timedelta(days=x[4])
        k = d.year * 100 + d.month
        months[k] = months.get(k, 0) + 1
    for k in sorted(months):
        lines.append("month|%d|%d" % (k, months[k]))
    top_cases = sorted(by_case.items(), key=lambda kv: (-kv[1][1], kv[0]))[:10]
    for i, (cid, (cnt, sc)) in enumerate(top_cases, 1):
        lines.append("topCase|%d|%s|count=%d|score=%d" % (i, cid, cnt, sc))
    by_sus: Dict[str, Tuple[int, int]] = {}
    for x, sc in scored:
        if x[1] is not None:
            cnt, tot = by_sus.get(x[1], (0, 0))
            by_sus[x[1]] = (cnt + 1, tot + sc)
    sus_map = {s[0]: s for s in suspects}
    ranked = []
    for sid, (cnt, sc) in by_sus.items():
        if sid in sus_map:
            susp = sus_map[sid][2]
            ranked.append((sid, cnt, sc, susp, sc + susp))
    ranked.sort(key=lambda r: (-r[4], r[0]))
    for i, (sid, cnt, sc, susp, pr) in enumerate(ranked[:10], 1):
        lines.append(
            "topSuspect|%d|%s|count=%d|score=%d|suspicion=%d|priority=%d"
            % (i, sid, cnt, sc, susp, pr)
        )
    for st in CASE_STATUS:
        ids = [c[0] for c in cases if c[1] == st]
        lines.append(
            "caseStatus|%s|cases=%d|evidences=%d"
            % (st, len(ids), sum(by_case.get(i, (0, 0))[0] for i in ids))
        )
    for st in SUSPECT_STATUS:
        sel = [s[2] for s in suspects if s[3] == st]
        avg = (sum(sel) * 100) // len(sel) if sel else 0
        lines.append("suspectStatus|%s|count=%d|avgSuspicionX100=%d" % (st, len(sel), avg))
    return lines


def checksum(lines: List[str]) -> str:
    """Calcula el SHA-256 en hexadecimal minúsculas de las líneas unidas por newline."""
    return hashlib.sha256("\n".join(lines).encode("utf-8")).hexdigest()
