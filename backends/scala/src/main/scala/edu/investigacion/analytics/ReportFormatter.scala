package edu.investigacion.analytics

import java.security.MessageDigest

object ReportFormatter:

  def formatReport(report: AnalyticsReport): List[String] =
    val totalLine = s"totals|valid=${report.totals.valid}|score=${report.totals.score}|maxScore=${report.totals.maxScore}|casesWithEvidence=${report.totals.casesWithEvidence}"

    val typeLines = report.typeScores.map { ts =>
      s"type|${ts.`type`.toString}|count=${ts.count}|score=${ts.score}"
    }

    val monthLines = report.months.map { m =>
      s"month|${m.yearMonth}|${m.count}"
    }

    val caseLines = report.topCases.map { tc =>
      s"topCase|${tc.rank}|${tc.caseId}|count=${tc.count}|score=${tc.score}"
    }

    val suspectLines = report.topSuspects.map { ts =>
      s"topSuspect|${ts.rank}|${ts.suspectId}|count=${ts.count}|score=${ts.score}|suspicion=${ts.suspicion}|priority=${ts.priority}"
    }

    val caseStatusLines = report.caseStatuses.map { cs =>
      s"caseStatus|${cs.status.toString}|cases=${cs.cases}|evidences=${cs.evidences}"
    }

    val suspectStatusLines = report.suspectStatuses.map { ss =>
      s"suspectStatus|${ss.status.toString}|count=${ss.count}|avgSuspicionX100=${ss.avgSuspicionX100}"
    }

    List(totalLine) ++ typeLines ++ monthLines ++ caseLines ++ suspectLines ++ caseStatusLines ++ suspectStatusLines

  def computeChecksum(lines: List[String]): String =
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = lines.mkString("\n").getBytes("UTF-8")
    val hash = digest.digest(bytes)
    hash.map("%02x".format(_)).mkString
