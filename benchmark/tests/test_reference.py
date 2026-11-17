"""
test_reference.py - Pruebas unitarias de la referencia canónica en Python.
Verifica que reference.py reproduzca exactamente los cinco checksums oficiales y las líneas canónicas de T.
"""

from pathlib import Path
import pytest

from benchmark.bench.reference import generate, pipeline, checksum, SCENARIOS

GOLDEN_CHECKSUMS = {
    "T": "bf5c6284888610a729ceeebf66b32d45854018ff61f0cf1867f67fba67d2a91e",
    "S": "a47b9117ce5d4a2ebf2b53a9d315435efcd58da1b54027647e7c83ea2c1941a9",
    "M": "0d2258e413ea3166ae104f79c4f6ec51ef6bd1595a8c0ed27bcdc97aa4ff58c4",
    "L": "888c0e28d9b7f93fbc48994aa3ac4f466761c16794b34d00e9edc1b02ac5c6b5",
    "XL": "fda8d3a8a73432d78b1cd451ab10fa36cf13902e8f235d1f5f851590f32ad475",
}


@pytest.mark.parametrize("scenario,expected_hash", list(GOLDEN_CHECKSUMS.items()))
def test_golden_checksums(scenario: str, expected_hash: str):
    cases, suspects, evid = generate(scenario, seed=42)
    lines = pipeline(cases, suspects, evid)
    actual_hash = checksum(lines)
    assert (
        actual_hash == expected_hash
    ), f"Fallo de checksum para escenario {scenario}: {actual_hash} != {expected_hash}"


def test_t_canonical_lines_match_golden_file():
    cases, suspects, evid = generate("T", seed=42)
    lines = pipeline(cases, suspects, evid)

    # Comparar contra shared/golden/T.txt
    golden_path = Path(__file__).resolve().parents[2] / "shared" / "golden" / "T.txt"
    assert golden_path.exists(), f"Archivo maestro {golden_path} no existe"

    golden_lines = golden_path.read_text(encoding="utf-8").splitlines()
    assert len(lines) == len(golden_lines)
    for idx, (act, exp) in enumerate(zip(lines, golden_lines)):
        assert act == exp, f"Línea #{idx+1} difiere: '{act}' != '{exp}'"
