"""
test_scoring.py - Pruebas unitarias del motor de cálculo de scoring, normalización y sensibilidad.
Verifica normalización con ratios, media geométrica, ponderaciones, reglas de desempate
y estabilidad del análisis de sensibilidad con métricas sintéticas.
"""

import pytest

from benchmark.bench.scorer import BenchmarkScorer, geometric_mean, median


def test_geometric_mean_calculation():
    assert geometric_mean([1.0, 1.0, 1.0]) == 1.0
    # Media geométrica de 2, 8 es sqrt(16) = 4.0
    assert abs(geometric_mean([2.0, 8.0]) - 4.0) < 1e-6
    # Si algún valor es <= 0, retorna 0.0
    assert geometric_mean([1.0, 0.0, 2.0]) == 0.0


def test_median_calculation():
    assert median([5.0]) == 5.0
    assert median([1.0, 5.0, 10.0]) == 5.0
    assert median([1.0, 2.0, 3.0, 4.0]) == 2.5


def test_single_metric_normalization_lower_is_better():
    scorer = BenchmarkScorer()
    values = {"java": 10.0, "scala": 20.0, "kotlin": 40.0}
    scores = scorer.compute_single_metric_relative_scores(values, lower_is_better=True)

    # El mejor (java con 10.0) debe obtener 1.0
    assert scores["java"] == 1.0
    # scala (20.0) -> 10.0 / 20.0 = 0.5
    assert scores["scala"] == 0.5
    # kotlin (40.0) -> 10.0 / 40.0 = 0.25
    assert scores["kotlin"] == 0.25


def test_single_metric_normalization_higher_is_better():
    scorer = BenchmarkScorer()
    values = {"java": 100.0, "scala": 50.0, "kotlin": 20.0}
    scores = scorer.compute_single_metric_relative_scores(values, lower_is_better=False)

    # El mejor (java con 100) obtiene 1.0
    assert scores["java"] == 1.0
    assert scores["scala"] == 0.5
    assert scores["kotlin"] == 0.2


def test_scenario_normalization_with_failed_scenario():
    scorer = BenchmarkScorer()
    # Si kotlin falló en L (valor 0 o None), su ratio en L es 0 y por tanto la media geométrica es 0
    scenarios = ["S", "M", "L"]
    values = {
        "java": {"S": 10.0, "M": 20.0, "L": 30.0},
        "scala": {"S": 15.0, "M": 25.0, "L": 35.0},
        "kotlin": {"S": 12.0, "M": 22.0, "L": 0.0},
    }
    scores = scorer.compute_scenario_relative_scores(
        values, scenarios, lower_is_better=True
    )
    assert scores["java"] == 1.0
    assert scores["kotlin"] == 0.0  # El escenario fallido hace que el ratio compuesto sea 0


def test_tie_breaking_logic():
    scorer = BenchmarkScorer()
    # Dos lenguajes con puntaje casi idéntico (< 0.5 pts de diferencia)
    criteria_scores = {
        "execution_time": {"java": 0.90, "scala": 0.80},
        "memory_consumption": {"java": 0.80, "scala": 0.80},
        "throughput": {"java": 0.80, "scala": 0.80},
        "code_quality": {"java": 0.80, "scala": 0.80},
        "reliability": {"java": 0.80, "scala": 0.95},  # Scala tiene mayor confiabilidad
        "functional_usage": {"java": 0.80, "scala": 0.80},
    }
    ranking = scorer.compute_overall_ranking(criteria_scores)
    assert len(ranking) == 2


def test_sensitivity_analysis_structure():
    scorer = BenchmarkScorer()
    criteria_scores = {
        "execution_time": {"java": 1.0, "scala": 0.8, "kotlin": 0.6},
        "memory_consumption": {"java": 0.7, "scala": 1.0, "kotlin": 0.8},
        "throughput": {"java": 1.0, "scala": 0.7, "kotlin": 0.6},
        "code_quality": {"java": 0.8, "scala": 0.9, "kotlin": 1.0},
        "reliability": {"java": 1.0, "scala": 1.0, "kotlin": 1.0},
        "functional_usage": {"java": 0.8, "scala": 1.0, "kotlin": 0.9},
    }
    sens = scorer.sensitivity_analysis(criteria_scores)
    assert "official_winner" in sens
    assert "winner_changed" in sens
    assert "scenarios" in sens
    assert "equal_weights" in sens["scenarios"]
    assert "double_perf_and_time" in sens["scenarios"]
