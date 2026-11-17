package edu.investigacion.analytics

import java.time.LocalDate

object AnalyticsPipeline:

  def run(
      cases: Seq[CaseRec],
      suspects: Seq[SuspectRec],
      evidences: Seq[EvidenceRec]
  ): AnalyticsReport =
    val validEvidences = evidences.filter(e => e.active && e.relevance >= AnalyticsConstants.MinRelevance)

    val scored = validEvidences.map(e => (e, e.relevance * e.`type`.weight))

    val totalScore = scored.map(_._2).sum
    val maxScore = scored.foldLeft(0L)((acc, pair) => if pair._2 > acc then pair._2 else acc)

    val byCase: Map[String, (Long, Long)] =
      scored
        .groupBy(_._1.caseId)
        .map { case (cid, list) =>
          val count = list.size.toLong
          val score = list.map(_._2).sum
          (cid, (count, score))
        }

    val totals = Totals(
      valid = validEvidences.size.toLong,
      score = totalScore,
      maxScore = maxScore,
      casesWithEvidence = byCase.size.toLong
    )

    val typeScores: List[TypeScore] =
      EvidenceType.values.toList.map { t =>
        val sel = scored.filter(_._1.`type` == t).map(_._2)
        TypeScore(t, sel.size.toLong, sel.sum)
      }

    val months: List[MonthCount] =
      scored
        .map { case (e, _) =>
          val d = LocalDate.ofEpochDay(e.collectedEpochDay)
          d.getYear.toLong * 100L + d.getMonthValue.toLong
        }
        .groupBy(identity)
        .map { case (ym, list) =>
          MonthCount(ym, list.size.toLong)
        }
        .toList
        .sortBy(_.yearMonth)

    val topCases: List[TopCase] =
      byCase.toList
        .sortWith { case ((id1, (_, sc1)), (id2, (_, sc2))) =>
          if sc1 != sc2 then sc1 > sc2
          else id1 < id2
        }
        .take(10)
        .zipWithIndex
        .map { case ((cid, (cnt, sc)), idx) =>
          TopCase(idx + 1, cid, cnt, sc)
        }

    val susMap: Map[String, SuspectRec] =
      suspects.map(s => s.id -> s).toMap

    val bySus: Map[String, (Long, Long)] =
      scored
        .flatMap { case (e, sc) =>
          e.suspectId.map(sid => (sid, sc))
        }
        .groupBy(_._1)
        .map { case (sid, list) =>
          (sid, (list.size.toLong, list.map(_._2).sum))
        }

    val topSuspects: List[TopSuspect] =
      bySus
        .flatMap { case (sid, (cnt, sc)) =>
          susMap.get(sid).map { s =>
            val susp = s.suspicionLevel
            val priority = sc + susp
            (sid, cnt, sc, susp, priority)
          }
        }
        .toList
        .sortWith { case ((sid1, _, _, _, pr1), (sid2, _, _, _, pr2)) =>
          if pr1 != pr2 then pr1 > pr2
          else sid1 < sid2
        }
        .take(10)
        .zipWithIndex
        .map { case ((sid, cnt, sc, susp, pr), idx) =>
          TopSuspect(idx + 1, sid, cnt, sc, susp, pr)
        }

    val caseStatuses: List[CaseStatusSummary] =
      CaseStatus.values.toList.map { st =>
        val matching = cases.filter(_.status == st)
        val evCount = matching.flatMap(c => byCase.get(c.id).map(_._1)).sum
        CaseStatusSummary(st, matching.size.toLong, evCount)
      }

    val suspectStatuses: List[SuspectStatusSummary] =
      SuspectStatus.values.toList.map { st =>
        val sel = suspects.filter(_.status == st).map(_.suspicionLevel)
        val avg = if sel.nonEmpty then (sel.sum * 100L) / sel.size.toLong else 0L
        SuspectStatusSummary(st, sel.size.toLong, avg)
      }

    AnalyticsReport(
      totals = totals,
      typeScores = typeScores,
      months = months,
      topCases = topCases,
      topSuspects = topSuspects,
      caseStatuses = caseStatuses,
      suspectStatuses = suspectStatuses
    )
