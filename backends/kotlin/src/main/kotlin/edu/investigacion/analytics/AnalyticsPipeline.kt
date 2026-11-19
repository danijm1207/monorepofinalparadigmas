package edu.investigacion.analytics

import java.time.LocalDate

object AnalyticsPipeline {
    private const val MIN_RELEVANCE = 2L

    private data class ScoredEvidence(
        val evidence: EvidenceRec,
        val score: Long
    )

    fun run(
        cases: List<CaseRec>,
        suspects: List<SuspectRec>,
        evidences: List<EvidenceRec>
    ): AnalyticsReport {
        // 1. Filtrar evidencias activas y con relevancia mínima
        val valid = evidences.filter { it.active && it.relevance >= MIN_RELEVANCE }

        // 2. Mapear con puntuación
        val scored = valid.map { ScoredEvidence(it, it.relevance * it.type.weight) }

        // 3. Totales globales
        val totalScore = scored.fold(0L) { acc, item -> acc + item.score }
        val maxScore = scored.map { it.score }.maxOrNull() ?: 0L

        // 4. Agrupación por caso para estadísticas y top casos
        val byCase = scored.groupBy { it.evidence.caseId }
            .mapValues { (_, items) ->
                val count = items.size.toLong()
                val score = items.sumOf { it.score }
                Pair(count, score)
            }
        val casesWithEvidenceCount = byCase.size.toLong()

        // 5. Estadísticas por tipo
        val byType = EvidenceType.entries.map { t ->
            val matching = scored.filter { it.evidence.type == t }
            TypeSummary(
                type = t,
                count = matching.size.toLong(),
                score = matching.sumOf { it.score }
            )
        }

        // 6. Estadísticas por mes (año * 100 + mes)
        val byMonth = scored.groupBy { item ->
            val date = LocalDate.ofEpochDay(item.evidence.collectedEpochDay)
            date.year.toLong() * 100L + date.monthValue.toLong()
        }
            .map { (yearMonth, items) -> MonthSummary(yearMonth, items.size.toLong()) }
            .sortedBy { it.yearMonth }

        // 7. Top 10 casos ordenados por score desc, id asc
        val topCases = byCase.entries
            .map { (caseId, stats) -> Triple(caseId, stats.first, stats.second) }
            .sortedWith(
                compareByDescending<Triple<String, Long, Long>> { it.third }
                    .thenBy { it.first }
            )
            .take(10)
            .mapIndexed { index, (caseId, count, score) ->
                TopCaseSummary(
                    rank = index + 1,
                    caseId = caseId,
                    count = count,
                    score = score
                )
            }

        // 8. Top 10 sospechosos
        val suspectMap = suspects.associateBy { it.id }
        val bySuspect = scored.filter { it.evidence.suspectId?.isNotEmpty() == true }
            .map { Pair(it.evidence.suspectId.orEmpty(), it.score) }
            .groupBy { it.first }
            .mapValues { (_, items) ->
                val count = items.size.toLong()
                val score = items.sumOf { it.second }
                Pair(count, score)
            }

        val topSuspects = bySuspect.entries
            .filter { (susId, _) -> suspectMap.containsKey(susId) }
            .map { (susId, stats) ->
                val suspect = suspectMap.getValue(susId)
                val suspicion = suspect.suspicionLevel
                val priority = stats.second + suspicion
                Triple(susId, stats, Pair(suspicion, priority))
            }
            .sortedWith(
                compareByDescending<Triple<String, Pair<Long, Long>, Pair<Long, Long>>> { it.third.second }
                    .thenBy { it.first }
            )
            .take(10)
            .mapIndexed { index, (susId, stats, extra) ->
                TopSuspectSummary(
                    rank = index + 1,
                    suspectId = susId,
                    count = stats.first,
                    score = stats.second,
                    suspicion = extra.first,
                    priority = extra.second
                )
            }

        // 9. Estadísticas por estado de caso (usa flatMap para aplanar casos y sus evidencias asociadas)
        val byCaseStatus = CaseStatus.entries.map { st ->
            val matchingCases = cases.filter { it.status == st }
            val evidenceCount = matchingCases
                .flatMap { caseRec -> listOf(byCase[caseRec.id]?.first ?: 0L) }
                .sumOf { it }
            CaseStatusSummary(
                status = st,
                cases = matchingCases.size.toLong(),
                evidences = evidenceCount
            )
        }

        // 10. Estadísticas por estado de sospechoso
        val bySuspectStatus = SuspectStatus.entries.map { st ->
            val matchingSuspects = suspects.filter { it.status == st }
            val count = matchingSuspects.size.toLong()
            val totalSuspicion = matchingSuspects.sumOf { it.suspicionLevel }
            val avgSuspicionX100 = if (count > 0L) {
                (totalSuspicion * 100L) / count
            } else {
                0L
            }
            SuspectStatusSummary(
                status = st,
                count = count,
                avgSuspicionX100 = avgSuspicionX100
            )
        }

        return AnalyticsReport(
            validEvidencesCount = valid.size.toLong(),
            totalScore = totalScore,
            maxScore = maxScore,
            casesWithEvidenceCount = casesWithEvidenceCount,
            byType = byType,
            byMonth = byMonth,
            topCases = topCases,
            topSuspects = topSuspects,
            byCaseStatus = byCaseStatus,
            bySuspectStatus = bySuspectStatus
        )
    }
}
