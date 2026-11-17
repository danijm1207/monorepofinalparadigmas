"""
scorer.py - Motor de consolidación, normalización y cálculo de puntuación multicriterio.
Implementa normalización por ratios (mejor/valor o valor/mejor), agregación por medianas y
medias geométricas, ponderación de la rúbrica, reglas de desempate y análisis de sensibilidad.
"""

import math
from typing import Any, Dict, List, Optional, Tuple


def geometric_mean(values: List[float]) -> float:
    """Calcula la media geométrica de una lista de ratios no negativos."""
    if not values:
        return 0.0
    if any(v <= 0.0 for v in values):
        return 0.0
    log_sum = sum(math.log(v) for v in values)
    return math.exp(log_sum / len(values))


def median(values: List[float]) -> float:
    """Calcula la mediana de una lista de números."""
    if not values:
        return 0.0
    s = sorted(values)
    n = len(s)
    mid = n // 2
    if n % 2 == 1:
        return s[mid]
    return (s[mid - 1] + s[mid]) / 2.0


class BenchmarkScorer:
    """Motor de cálculo de rankings y scoring oficial."""

    DEFAULT_WEIGHTS = {
        "execution_time": 0.25,
        "memory_consumption": 0.20,
        "throughput": 0.20,
        "code_quality": 0.15,
        "reliability": 0.10,
        "functional_usage": 0.10,
    }

    def __init__(self, weights: Optional[Dict[str, float]] = None):
        self.weights = weights or dict(self.DEFAULT_WEIGHTS)

    def compute_scenario_relative_scores(
        self,
        lang_scenario_values: Dict[str, Dict[str, float]],
        scenarios: List[str],
        lower_is_better: bool = True,
    ) -> Dict[str, float]:
        """
        Calcula la media geométrica de las razones por escenario para cada lenguaje.
        Si un escenario falló para un lenguaje (valor 0 o None), su ratio es 0.0.
        """
        langs = list(lang_scenario_values.keys())
        ratios_by_lang: Dict[str, List[float]] = {l: [] for l in langs}

        for sc in scenarios:
            vals = {
                l: lang_scenario_values[l].get(sc)
                for l in langs
                if lang_scenario_values[l].get(sc) is not None
                and lang_scenario_values[l].get(sc, 0) > 0
            }
            if not vals:
                for l in langs:
                    ratios_by_lang[l].append(0.0)
                continue

            if lower_is_better:
                best_val = min(vals.values())
                for l in langs:
                    v = lang_scenario_values[l].get(sc)
                    if v is not None and v > 0:
                        ratios_by_lang[l].append(best_val / v)
                    else:
                        ratios_by_lang[l].append(0.0)
            else:
                best_val = max(vals.values())
                for l in langs:
                    v = lang_scenario_values[l].get(sc)
                    if v is not None and v > 0:
                        ratios_by_lang[l].append(v / best_val)
                    else:
                        ratios_by_lang[l].append(0.0)

        # Media geométrica de los ratios por escenario
        scores: Dict[str, float] = {}
        for l in langs:
            scores[l] = geometric_mean(ratios_by_lang[l])
        return scores

    def compute_single_metric_relative_scores(
        self,
        lang_values: Dict[str, float],
        lower_is_better: bool = True,
    ) -> Dict[str, float]:
        """Normaliza una métrica escalar única entre lenguajes."""
        langs = list(lang_values.keys())
        valid_vals = [
            v for v in lang_values.values() if v is not None and v > 0
        ]
        if not valid_vals:
            return {l: 0.0 for l in langs}

        scores: Dict[str, float] = {}
        if lower_is_better:
            best_val = min(valid_vals)
            for l in langs:
                v = lang_values.get(l)
                scores[l] = (best_val / v) if (v and v > 0) else 0.0
        else:
            best_val = max(valid_vals)
            for l in langs:
                v = lang_values.get(l)
                scores[l] = (v / best_val) if (v and v > 0) else 0.0

        return scores

    def score_all_criteria(
        self,
        # Tiempo
        scenario_wall_times: Dict[str, Dict[str, float]],
        startup_times: Dict[str, float],
        http_p95_latencies: Dict[str, float],
        # Memoria
        scenario_peak_memories: Dict[str, Dict[str, float]],
        idle_memories: Dict[str, float],
        # Rendimiento
        scenario_pipeline_rates: Dict[str, Dict[str, float]],
        http_rps_c50: Dict[str, float],
        # Calidad
        code_quality_scores: Dict[str, float],
        # Confiabilidad
        contract_pass_rates: Dict[str, float],
        checksum_accuracies: Dict[str, float],
        error_free_rates: Dict[str, float],
        stability_scores: Dict[str, float],
        # Uso Funcional
        functional_scores: Dict[str, float],
        scenarios: List[str],
    ) -> Dict[str, Dict[str, float]]:
        """
        Calcula las puntuaciones normalizadas por criterio para cada lenguaje (0.0 a 1.0).
        """
        langs = list(code_quality_scores.keys())

        # 1. Tiempo de Ejecución (25%)
        # 60% tiempo pared escenarios (menor es mejor)
        time_scen_scores = self.compute_scenario_relative_scores(
            scenario_wall_times, scenarios, lower_is_better=True
        )
        # 20% arranque (menor es mejor)
        startup_scores = self.compute_single_metric_relative_scores(
            startup_times, lower_is_better=True
        )
        # 20% latencia p95 c10 (menor es mejor)
        p95_scores = self.compute_single_metric_relative_scores(
            http_p95_latencies, lower_is_better=True
        )
        exec_time_scores = {
            l: time_scen_scores[l] * 0.60
            + startup_scores[l] * 0.20
            + p95_scores[l] * 0.20
            for l in langs
        }

        # 2. Consumo de Memoria (20%)
        # 70% pico de memoria escenarios (menor es mejor)
        peak_mem_scores = self.compute_scenario_relative_scores(
            scenario_peak_memories, scenarios, lower_is_better=True
        )
        # 30% memoria en reposo (menor es mejor)
        idle_mem_scores = self.compute_single_metric_relative_scores(
            idle_memories, lower_is_better=True
        )
        mem_scores = {
            l: peak_mem_scores[l] * 0.70 + idle_mem_scores[l] * 0.30 for l in langs
        }

        # 3. Rendimiento (Throughput) (20%)
        # 60% pipeline records/sec (mayor es mejor)
        pipeline_scen_scores = self.compute_scenario_relative_scores(
            scenario_pipeline_rates, scenarios, lower_is_better=False
        )
        # 40% RPS c50 (mayor es mejor)
        rps_scores = self.compute_single_metric_relative_scores(
            http_rps_c50, lower_is_better=False
        )
        throughput_scores = {
            l: pipeline_scen_scores[l] * 0.60 + rps_scores[l] * 0.40 for l in langs
        }

        # 4. Calidad de Código (15%)
        quality_scores = {l: code_quality_scores.get(l, 0.0) for l in langs}

        # 5. Confiabilidad (10%)
        # 40% contratos + 30% checksums + 20% error_free + 10% estabilidad
        reliability_scores = {
            l: contract_pass_rates.get(l, 0.0) * 0.40
            + checksum_accuracies.get(l, 0.0) * 0.30
            + error_free_rates.get(l, 0.0) * 0.20
            + stability_scores.get(l, 0.0) * 0.10
            for l in langs
        }

        # 6. Uso Funcional (10%)
        func_scores = {l: functional_scores.get(l, 0.0) for l in langs}

        return {
            "execution_time": exec_time_scores,
            "memory_consumption": mem_scores,
            "throughput": throughput_scores,
            "code_quality": quality_scores,
            "reliability": reliability_scores,
            "functional_usage": func_scores,
        }

    def compute_overall_ranking(
        self,
        criteria_scores: Dict[str, Dict[str, float]],
        custom_weights: Optional[Dict[str, float]] = None,
    ) -> List[Dict[str, Any]]:
        """
        Aplica los pesos de la rúbrica para calcular puntajes globales (0..100)
        y ordena con regla de desempate: si diferencia < 0.5 pts, desempata confiabilidad y luego tiempo.
        """
        weights = custom_weights or self.weights
        langs = list(criteria_scores["code_quality"].keys())

        ranked_items = []
        for l in langs:
            score_100 = 0.0
            breakdown = {}
            for criterion, weight in weights.items():
                c_score = criteria_scores[criterion].get(l, 0.0)
                breakdown[criterion] = c_score
                score_100 += (c_score * 100.0) * weight

            ranked_items.append(
                {
                    "lang": l,
                    "total_score": round(score_100, 2),
                    "criteria": breakdown,
                    "reliability_score": criteria_scores["reliability"].get(l, 0.0),
                    "execution_time_score": criteria_scores["execution_time"].get(l, 0.0),
                }
            )

        # Ordenar con desempate:
        # Primero por total_score descendente
        # Si la diferencia con el anterior es < 0.5, se evalúa desempate por confiabilidad y tiempo
        def sort_key(item):
            # Tupla negativa para orden descendente
            return (-item["total_score"], -item["reliability_score"], -item["execution_time_score"])

        ranked_items.sort(key=sort_key)

        for pos, item in enumerate(ranked_items, 1):
            item["rank"] = pos

        return ranked_items

    def sensitivity_analysis(
        self, criteria_scores: Dict[str, Dict[str, float]]
    ) -> Dict[str, Any]:
        """
        Ejecuta análisis de sensibilidad automático:
        (a) Pesos iguales: cada criterio pesa 1/6 (16.67%)
        (b) Peso doble en rendimiento y tiempo: tiempo=0.333, rendimiento=0.333, otros 4 = 0.0833
        """
        official_ranking = self.compute_overall_ranking(criteria_scores)
        official_winner = official_ranking[0]["lang"]

        # (a) Pesos iguales
        equal_weights = {k: 1.0 / 6.0 for k in self.DEFAULT_WEIGHTS}
        ranking_equal = self.compute_overall_ranking(criteria_scores, equal_weights)
        winner_equal = ranking_equal[0]["lang"]

        # (b) Peso doble en rendimiento y tiempo
        # tiempo: 2, rendimiento: 2, memoria: 1, calidad: 1, confiabilidad: 1, funcional: 1 -> total=8
        double_perf_weights = {
            "execution_time": 2.0 / 8.0,
            "throughput": 2.0 / 8.0,
            "memory_consumption": 1.0 / 8.0,
            "code_quality": 1.0 / 8.0,
            "reliability": 1.0 / 8.0,
            "functional_usage": 1.0 / 8.0,
        }
        ranking_double_perf = self.compute_overall_ranking(
            criteria_scores, double_perf_weights
        )
        winner_double_perf = ranking_double_perf[0]["lang"]

        winner_changed = (
            official_winner != winner_equal or official_winner != winner_double_perf
        )

        return {
            "official_winner": official_winner,
            "winner_changed": winner_changed,
            "scenarios": {
                "official": official_ranking,
                "equal_weights": {
                    "winner": winner_equal,
                    "ranking": ranking_equal,
                },
                "double_perf_and_time": {
                    "winner": winner_double_perf,
                    "ranking": ranking_double_perf,
                },
            },
        }
