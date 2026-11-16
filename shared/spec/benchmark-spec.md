# Especificación del Benchmark Funcional

Este documento describe la especificación técnica vinculante y exacta del benchmark funcional compartido para las tres implementaciones (Java, Scala, Kotlin) del Sistema de Investigación Criminal.

---

## 1. Objetivo

Los tres backends deben ejecutar **EXACTAMENTE** el mismo cálculo sobre los mismos datos sintéticos deterministas y producir el mismo checksum SHA-256, el cual debe coincidir bit a bit con la referencia implementada en Python (`reference.py`). Cualquier discrepancia en el checksum representa un defecto de implementación en el backend evaluado.

---

## 2. Escenarios y Volúmenes de Datos

| Escenario | Propósito | Casos ($C$) | Sospechosos ($S$) | Evidencias ($E$) | Total Registros |
|:---:|:---|---:|---:|---:|---:|
| **T** | Verificación rápida / Smoke | 20 | 60 | 200 | 280 |
| **S** | Carga Pequeña (Small) | 1 000 | 3 000 | 10 000 | 14 000 |
| **M** | Carga Mediana (Medium) | 10 000 | 30 000 | 100 000 | 140 000 |
| **L** | Carga Grande (Large) | 50 000 | 150 000 | 500 000 | 700 000 |
| **XL**| Estrés / Máxima Carga | 100 000 | 300 000 | 1 000 000 | 1 400 000 |

* **Semilla determinista por defecto:** `42` (entero de 32 bits sin signo: `0..4294967295`).

---

## 3. Generador Determinista y Pipeline de Referencia

La referencia canónica escrita en Python 3 es la fuente única de verdad:

```python
import hashlib
from datetime import date, timedelta

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
    "XL": (100000, 300000, 1000000)
}
MIN_RELEVANCE = 2

class Rng:
    def __init__(self, seed):
        self.s = seed & 0xFFFFFFFF
    def next(self):
        self.s = (self.s * 1664525 + 1013904223) & 0xFFFFFFFF
        return self.s
    def next_int(self, n):
        return (self.next() >> 8) % n

def generate(scenario, seed=42):
    c, s, e = SCENARIOS[scenario]
    r = Rng(seed)
    cases = []
    for i in range(c):
        st = CASE_STATUS[r.next_int(3)]
        day = BASE_DAY + r.next_int(1096)
        cases.append(("C%07d" % i, st, day))
    suspects = []
    for j in range(s):
        ci = r.next_int(c)
        sus = r.next_int(101)
        st = SUSPECT_STATUS[r.next_int(3)]
        suspects.append(("S%07d" % j, "C%07d" % ci, sus, st))
    evid = []
    for k in range(e):
        ci = r.next_int(c)
        h = r.next_int(100)
        si = r.next_int(s)
        t = TYPES[r.next_int(5)]
        rel = 1 + r.next_int(5)
        day = BASE_DAY + r.next_int(1096)
        a = r.next_int(100)
        evid.append(("C%07d" % ci, ("S%07d" % si) if h < 60 else None, t, rel, day, a < 95))
    return cases, suspects, evid

def pipeline(cases, suspects, evid):
    valid = [x for x in evid if x[5] and x[3] >= MIN_RELEVANCE]
    scored = [(x, x[3] * WEIGHT[x[2]]) for x in valid]
    lines = []
    total = sum(sc for _, sc in scored)
    mx = max((sc for _, sc in scored), default=0)
    by_case = {}
    for x, sc in scored:
        cnt, tot = by_case.get(x[0], (0, 0))
        by_case[x[0]] = (cnt + 1, tot + sc)
    lines.append("totals|valid=%d|score=%d|maxScore=%d|casesWithEvidence=%d" % (len(valid), total, mx, len(by_case)))
    for t in TYPES:
        sel = [sc for x, sc in scored if x[2] == t]
        lines.append("type|%s|count=%d|score=%d" % (t, len(sel), sum(sel)))
    months = {}
    for x, _ in scored:
        d = date(1970, 1, 1) + timedelta(days=x[4])
        k = d.year * 100 + d.month
        months[k] = months.get(k, 0) + 1
    for k in sorted(months):
        lines.append("month|%d|%d" % (k, months[k]))
    top_cases = sorted(by_case.items(), key=lambda kv: (-kv[1][1], kv[0]))[:10]
    for i, (cid, (cnt, sc)) in enumerate(top_cases, 1):
        lines.append("topCase|%d|%s|count=%d|score=%d" % (i, cid, cnt, sc))
    by_sus = {}
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
        lines.append("topSuspect|%d|%s|count=%d|score=%d|suspicion=%d|priority=%d" % (i, sid, cnt, sc, susp, pr))
    for st in CASE_STATUS:
        ids = [c[0] for c in cases if c[1] == st]
        lines.append("caseStatus|%s|cases=%d|evidences=%d" % (st, len(ids), sum(by_case.get(i, (0, 0))[0] for i in ids)))
    for st in SUSPECT_STATUS:
        sel = [s[2] for s in suspects if s[3] == st]
        avg = (sum(sel) * 100) // len(sel) if sel else 0
        lines.append("suspectStatus|%s|count=%d|avgSuspicionX100=%d" % (st, len(sel), avg))
    return lines

def checksum(lines):
    return hashlib.sha256("\n".join(lines).encode("utf-8")).hexdigest()
```

---

## 4. Requisitos de Implementación en cada Backend (`analytics/`)

Cada backend debe implementar en su lenguaje correspondiente (`edu.investigacion.analytics`):

1. **`Rng`**:
   - Generador lineal congruencial de 32 bits ($a = 1664525$, $c = 1013904223$, $m = 2^{32}$).
   - Estado: `(s * 1664525L + 1013904223L) & 0xFFFFFFFFL`.
   - `nextInt(n)`: `((next() >>> 8) % n)`.
   - Es la **única** clase con estado mutable permitido en el módulo analytics.

2. **`ScenarioGenerator`**:
   - Genera `cases`, `suspects` y `evidences` consumiendo el `Rng` en el **mismo orden exacto** que la referencia:
     - 1. Todos los casos: `(status, day)`
     - 2. Todos los sospechosos: `(caseIndex, suspicion, status)`
     - 3. Todas las evidencias: `(caseIndex, h, suspectIndex, type, relevance, day, a)`. Siempre consume los 7 números por evidencia, incluso si `h >= 60` (donde `suspectId` queda como ausente / `None` / `null`).
   - Ids de texto generados con formato exacto: `C%07d` para casos y `S%07d` para sospechosos.

3. **Modelos Inmutables**:
   - `CaseRec(id, status, incidentEpochDay)`
   - `SuspectRec(id, caseId, suspicionLevel, status)`
   - `EvidenceRec(caseId, suspectId, type, relevance, collectedEpochDay, active)`
   - Enums:
     - `CaseStatus`: `OPEN`, `IN_PROGRESS`, `CLOSED`
     - `SuspectStatus`: `PERSON_OF_INTEREST`, `DETAINED`, `CLEARED`
     - `EvidenceType`: `PHYSICAL` (peso 10), `DIGITAL` (peso 12), `DOCUMENT` (peso 8), `TESTIMONY` (peso 6), `FORENSIC` (peso 15)
   - Constante `MIN_RELEVANCE = 2`.

4. **`AnalyticsPipeline.run(cases, suspects, evidences)`**:
   - Función **PURA** que retorna un reporte inmutable (`AnalyticsReport`).
   - Construido **exclusivamente** con operaciones funcionales de orden superior de la biblioteca estándar del lenguaje:
     - `filter`, `map`, `groupBy` / `groupingBy`, `reduce` / `fold` / `sum`, `flatMap`, `sorted` / `sortedWith` con comparadores explícitos, `take` / `limit`, y joins indexados mediante mapa (`Map[String, SuspectRec]`).
   - **Prohibido:** bucles (`for`, `while`), variables mutables reasignables (`var`), colecciones mutables, `null`, excepciones como control de flujo, efectos de I/O, concurrencia o colecciones paralelas (`parallelStream`, coroutines, `Future`, par collections).

5. **`ReportFormatter`**:
   - Formatea el reporte en las líneas canónicas de texto exactas sin salto de línea final antes de hashear.
   - Calcula el `checksum` como el hash SHA-256 en hexadecimal minúsculas de la concatenación con `\n` codificada en UTF-8.

6. **Reglas de Pureza de Imports**:
   - Los archivos `AnalyticsPipeline` y `ReportFormatter` no deben importar paquetes de I/O ni frameworks (`java.sql`, `java.io`, `java.nio`, `java.net`, `javax.*`, `jakarta.*`, `io.javalin.*`, `org.slf4j.*`, `com.zaxxer.*`, `com.fasterxml.*`).

---

## 5. Checksums y Salida Canónica (Golden Master)

Valores SHA-256 canónicos verificados con semilla `42`:

- **T:** `bf5c6284888610a729ceeebf66b32d45854018ff61f0cf1867f67fba67d2a91e`
- **S:** `a47b9117ce5d4a2ebf2b53a9d315435efcd58da1b54027647e7c83ea2c1941a9`
- **M:** `0d2258e413ea3166ae104f79c4f6ec51ef6bd1595a8c0ed27bcdc97aa4ff58c4`
- **L:** `888c0e28d9b7f93fbc48994aa3ac4f466761c16794b34d00e9edc1b02ac5c6b5`
- **XL:** `fda8d3a8a73432d78b1cd451ab10fa36cf13902e8f235d1f5f851590f32ad475`

La salida línea por línea para el escenario T se encuentra disponible en `shared/golden/T.txt`.
