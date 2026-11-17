package edu.investigacion.domain

enum class Role {
    ADMIN,
    USER
}

enum class RecordState {
    ACTIVE,
    INACTIVE
}

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

enum class EvidenceType {
    PHYSICAL,
    DIGITAL,
    DOCUMENT,
    TESTIMONY,
    FORENSIC
}

data class User(
    val id: String,
    val name: String,
    val email: String,
    val passwordHash: String,
    val role: Role,
    val state: RecordState,
    val createdAt: String,
    val updatedAt: String
)

data class UserPublic(
    val id: String,
    val name: String,
    val email: String,
    val role: Role,
    val createdAt: String
)

data class Case(
    val id: String,
    val caseNumber: Int,
    val title: String,
    val description: String,
    val location: String,
    val incidentDate: String,
    val status: CaseStatus,
    val imageUrl: String?,
    val state: RecordState,
    val ownerId: String,
    val createdAt: String,
    val updatedAt: String
)

data class Suspect(
    val id: String,
    val caseId: String,
    val fullName: String,
    val alias: String?,
    val description: String?,
    val suspicionLevel: Int,
    val status: SuspectStatus,
    val photoUrl: String?,
    val state: RecordState,
    val createdAt: String,
    val updatedAt: String
)

data class Evidence(
    val id: String,
    val caseId: String,
    val suspectId: String?,
    val title: String,
    val description: String,
    val type: EvidenceType,
    val collectedAt: String,
    val relevance: Int,
    val imageUrl: String?,
    val state: RecordState,
    val createdAt: String,
    val updatedAt: String
)

data class Pagination(
    val total: Long,
    val currentPage: Int,
    val limit: Int,
    val totalPages: Int
)

data class Page<T>(
    val data: List<T>,
    val pagination: Pagination
)
