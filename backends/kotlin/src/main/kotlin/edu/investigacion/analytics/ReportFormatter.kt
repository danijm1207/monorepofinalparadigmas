package edu.investigacion.analytics

import java.security.MessageDigest

object ReportFormatter {
    fun formatLines(report: AnalyticsReport): List<String> {
        val totalLine = listOf(
            "totals|valid=${report.validEvidencesCount}|score=${report.totalScore}|maxScore=${report.maxScore}|casesWithEvidence=${report.casesWithEvidenceCount}"
        )

        val typeLines = report.byType.map {
            "type|${it.type.name}|count=${it.count}|score=${it.score}"
        }

        val monthLines = report.byMonth.map {
            "month|${it.yearMonth}|${it.count}"
        }

        val topCaseLines = report.topCases.map {
            "topCase|${it.rank}|${it.caseId}|count=${it.count}|score=${it.score}"
        }

        val topSuspectLines = report.topSuspects.map {
            "topSuspect|${it.rank}|${it.suspectId}|count=${it.count}|score=${it.score}|suspicion=${it.suspicion}|priority=${it.priority}"
        }

        val caseStatusLines = report.byCaseStatus.map {
            "caseStatus|${it.status.name}|cases=${it.cases}|evidences=${it.evidences}"
        }

        val suspectStatusLines = report.bySuspectStatus.map {
            "suspectStatus|${it.status.name}|count=${it.count}|avgSuspicionX100=${it.avgSuspicionX100}"
        }

        return listOf(totalLine, typeLines, monthLines, topCaseLines, topSuspectLines, caseStatusLines, suspectStatusLines)
            .flatMap { it }
    }

    fun checksum(lines: List<String>): String {
        val content = lines.joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
