package edu.investigacion.domain

import java.util.UUID
import edu.investigacion.analytics.{CaseRec, SuspectRec, EvidenceRec}

trait UserRepository:
  def findById(id: UUID): Option[User]
  def findByEmail(email: String): Option[User]
  def create(user: User): User
  def findAll(filter: UserFilter, page: PageRequest): PageResult[User]
  def deactivate(id: UUID): Boolean

trait CaseRepository:
  def findById(id: UUID): Option[Case]
  def create(caseItem: Case): Case
  def update(caseItem: Case): Case
  def findAll(filter: CaseFilter, page: PageRequest): PageResult[Case]
  def deactivateCascade(id: UUID): Boolean

trait SuspectRepository:
  def findById(id: UUID): Option[Suspect]
  def create(suspect: Suspect): Suspect
  def update(suspect: Suspect): Suspect
  def findAll(filter: SuspectFilter, page: PageRequest): PageResult[Suspect]
  def deactivateAndUnlinkEvidences(id: UUID): Boolean

trait EvidenceRepository:
  def findById(id: UUID): Option[Evidence]
  def create(evidence: Evidence): Evidence
  def update(evidence: Evidence): Evidence
  def findAll(filter: EvidenceFilter, page: PageRequest): PageResult[Evidence]
  def deactivate(id: UUID): Boolean

trait AnalyticsRepository:
  def fetchActiveRecords(): (List[CaseRec], List[SuspectRec], List[EvidenceRec])
