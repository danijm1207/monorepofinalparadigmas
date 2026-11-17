package edu.investigacion.domain

import java.time.Instant
import java.util.UUID

enum Role:
  case ADMIN, USER

enum EntityState:
  case ACTIVE, INACTIVE

enum CaseStatus:
  case OPEN, IN_PROGRESS, CLOSED

enum SuspectStatus:
  case PERSON_OF_INTEREST, DETAINED, CLEARED

enum EvidenceType:
  case PHYSICAL, DIGITAL, DOCUMENT, TESTIMONY, FORENSIC

final case class User(
    id: UUID,
    name: String,
    email: String,
    passwordHash: String,
    role: Role,
    state: EntityState,
    createdAt: Instant,
    updatedAt: Instant
)

final case class Case(
    id: UUID,
    caseNumber: Long,
    title: String,
    description: String,
    location: String,
    incidentDate: Instant,
    status: CaseStatus,
    imageUrl: Option[String],
    state: EntityState,
    ownerId: UUID,
    createdAt: Instant,
    updatedAt: Instant
)

final case class Suspect(
    id: UUID,
    caseId: UUID,
    fullName: String,
    alias: Option[String],
    description: Option[String],
    suspicionLevel: Int,
    status: SuspectStatus,
    photoUrl: Option[String],
    state: EntityState,
    createdAt: Instant,
    updatedAt: Instant
)

final case class Evidence(
    id: UUID,
    caseId: UUID,
    suspectId: Option[UUID],
    title: String,
    description: String,
    `type`: EvidenceType,
    collectedAt: Instant,
    relevance: Int,
    imageUrl: Option[String],
    state: EntityState,
    createdAt: Instant,
    updatedAt: Instant
)

final case class UserFilter(
    q: Option[String] = None,
    role: Option[Role] = None
)

final case class CaseFilter(
    q: Option[String] = None,
    status: Option[CaseStatus] = None,
    from: Option[Instant] = None,
    to: Option[Instant] = None,
    ownerId: Option[UUID] = None
)

final case class SuspectFilter(
    caseId: Option[UUID] = None,
    status: Option[SuspectStatus] = None,
    q: Option[String] = None,
    ownerId: Option[UUID] = None
)

final case class EvidenceFilter(
    caseId: Option[UUID] = None,
    suspectId: Option[UUID] = None,
    typeFilter: Option[EvidenceType] = None,
    q: Option[String] = None,
    ownerId: Option[UUID] = None
)

final case class PageRequest(
    page: Int = 1,
    limit: Int = 10
)

final case class PageMeta(
    total: Long,
    currentPage: Int,
    limit: Int,
    totalPages: Int
)

final case class PageResult[A](
    data: List[A],
    pagination: PageMeta
)
