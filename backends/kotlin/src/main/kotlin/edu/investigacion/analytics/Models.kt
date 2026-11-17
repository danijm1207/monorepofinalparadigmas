package edu.investigacion.analytics

enum class CaseStatus {
    OPEN,
    IN_PROGRESS,
    CLOSED
}

enum class SuspectStatus {
    PERSON_OF_INTEREST,
    DETAINED,
    CLEARED
}

enum class EvidenceType(val weight: Long) {
    PHYSICAL(10L),
    DIGITAL(12L),
    DOCUMENT(8L),
    TESTIMONY(6L),
    FORENSIC(15L)
}

data class CaseRec(
    val id: String,
    val status: CaseStatus,
    val incidentEpochDay: Long
)

data class SuspectRec(
    val id: String,
    val caseId: String,
    val suspicionLevel: Long,
    val status: SuspectStatus
)

data class EvidenceRec(
    val caseId: String,
    val suspectId: String?,
    val type: EvidenceType,
    val relevance: Long,
    val collectedEpochDay: Long,
    val active: Boolean
)

data class TypeSummary(
    val type: EvidenceType,
    val count: Long,
    val score: Long
)

data class MonthSummary(
    val yearMonth: Long,
    val count: Long
)

data class TopCaseSummary(
    val rank: Int,
    val caseId: String,
    val count: Long,
    val score: Long
)

data class TopSuspectSummary(
    val rank: Int,
    val suspectId: String,
    val count: Long,
    val score: Long,
    val suspicion: Long,
    val priority: Long
)

data class CaseStatusSummary(
    val status: CaseStatus,
    val cases: Long,
    val evidences: Long
)

data class SuspectStatusSummary(
    val status: SuspectStatus,
    val count: Long,
    val avgSuspicionX100: Long
)

data class AnalyticsReport(
    val validEvidencesCount: Long,
    val totalScore: Long,
    val maxScore: Long,
    val casesWithEvidenceCount: Long,
    val byType: List<TypeSummary>,
    val byMonth: List<MonthSummary>,
    val topCases: List<TopCaseSummary>,
    val topSuspects: List<TopSuspectSummary>,
    val byCaseStatus: List<CaseStatusSummary>,
    val bySuspectStatus: List<SuspectStatusSummary>
)
