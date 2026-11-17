package edu.investigacion.analytics

enum CaseStatus:
  case OPEN, IN_PROGRESS, CLOSED

enum SuspectStatus:
  case PERSON_OF_INTEREST, DETAINED, CLEARED

enum EvidenceType(val weight: Long):
  case PHYSICAL extends EvidenceType(10L)
  case DIGITAL extends EvidenceType(12L)
  case DOCUMENT extends EvidenceType(8L)
  case TESTIMONY extends EvidenceType(6L)
  case FORENSIC extends EvidenceType(15L)

object AnalyticsConstants:
  val MinRelevance: Long = 2L
  val BaseEpochDay: Long = 19358L

final case class CaseRec(
    id: String,
    status: CaseStatus,
    incidentEpochDay: Long
)

final case class SuspectRec(
    id: String,
    caseId: String,
    suspicionLevel: Long,
    status: SuspectStatus
)

final case class EvidenceRec(
    caseId: String,
    suspectId: Option[String],
    `type`: EvidenceType,
    relevance: Long,
    collectedEpochDay: Long,
    active: Boolean
)

final case class Totals(
    valid: Long,
    score: Long,
    maxScore: Long,
    casesWithEvidence: Long
)

final case class TypeScore(
    `type`: EvidenceType,
    count: Long,
    score: Long
)

final case class MonthCount(
    yearMonth: Long,
    count: Long
)

final case class TopCase(
    rank: Int,
    caseId: String,
    count: Long,
    score: Long
)

final case class TopSuspect(
    rank: Int,
    suspectId: String,
    count: Long,
    score: Long,
    suspicion: Long,
    priority: Long
)

final case class CaseStatusSummary(
    status: CaseStatus,
    cases: Long,
    evidences: Long
)

final case class SuspectStatusSummary(
    status: SuspectStatus,
    count: Long,
    avgSuspicionX100: Long
)

final case class AnalyticsReport(
    totals: Totals,
    typeScores: List[TypeScore],
    months: List[MonthCount],
    topCases: List[TopCase],
    topSuspects: List[TopSuspect],
    caseStatuses: List[CaseStatusSummary],
    suspectStatuses: List[SuspectStatusSummary]
)
