package edu.investigacion.doubles

import java.util.UUID
import scala.collection.mutable
import edu.investigacion.domain.*

class InMemoryUserRepository extends UserRepository:
  private val store = mutable.Map[UUID, User]()

  def seed(user: User): Unit = store.put(user.id, user)

  override def findById(id: UUID): Option[User] =
    store.get(id).filter(_.state == EntityState.ACTIVE)

  override def findByEmail(email: String): Option[User] =
    store.values.find(u => u.email.equalsIgnoreCase(email.trim) && u.state == EntityState.ACTIVE)

  override def create(user: User): User =
    store.put(user.id, user)
    user

  override def findAll(filter: UserFilter, page: PageRequest): PageResult[User] =
    val filtered = store.values.filter(_.state == EntityState.ACTIVE).filter { u =>
      val matchesQ = filter.q.forall(term => u.name.toLowerCase.contains(term.toLowerCase) || u.email.toLowerCase.contains(term.toLowerCase))
      val matchesRole = filter.role.forall(_ == u.role)
      matchesQ && matchesRole
    }.toList.sortBy(_.createdAt).reverse

    val total = filtered.size.toLong
    val offset = (page.page - 1) * page.limit
    val data = filtered.slice(offset, offset + page.limit)
    val totalPages = if total == 0L then 0 else math.ceil(total.toDouble / page.limit).toInt
    PageResult(data, PageMeta(total, page.page, page.limit, totalPages))

  override def deactivate(id: UUID): Boolean =
    store.get(id) match
      case Some(u) if u.state == EntityState.ACTIVE =>
        store.put(id, u.copy(state = EntityState.INACTIVE))
        true
      case _ => false

class InMemoryCaseRepository extends CaseRepository:
  private val store = mutable.Map[UUID, Case]()
  private var nextCaseNumber = 1L

  def seed(c: Case): Unit = store.put(c.id, c)

  override def findById(id: UUID): Option[Case] =
    store.get(id).filter(_.state == EntityState.ACTIVE)

  override def create(caseItem: Case): Case =
    val c = caseItem.copy(caseNumber = nextCaseNumber)
    nextCaseNumber += 1
    store.put(c.id, c)
    c

  override def update(caseItem: Case): Case =
    store.put(caseItem.id, caseItem)
    caseItem

  override def findAll(filter: CaseFilter, page: PageRequest): PageResult[Case] =
    val filtered = store.values.filter(_.state == EntityState.ACTIVE).filter { c =>
      val matchesQ = filter.q.forall(term => c.title.toLowerCase.contains(term.toLowerCase) || c.location.toLowerCase.contains(term.toLowerCase))
      val matchesStatus = filter.status.forall(_ == c.status)
      val matchesFrom = filter.from.forall(!c.incidentDate.isBefore(_))
      val matchesTo = filter.to.forall(!c.incidentDate.isAfter(_))
      val matchesOwner = filter.ownerId.forall(_ == c.ownerId)
      matchesQ && matchesStatus && matchesFrom && matchesTo && matchesOwner
    }.toList.sortBy(_.createdAt).reverse

    val total = filtered.size.toLong
    val offset = (page.page - 1) * page.limit
    val data = filtered.slice(offset, offset + page.limit)
    val totalPages = if total == 0L then 0 else math.ceil(total.toDouble / page.limit).toInt
    PageResult(data, PageMeta(total, page.page, page.limit, totalPages))

  override def deactivateCascade(id: UUID): Boolean =
    store.get(id) match
      case Some(c) if c.state == EntityState.ACTIVE =>
        store.put(id, c.copy(state = EntityState.INACTIVE))
        true
      case _ => false

class InMemorySuspectRepository extends SuspectRepository:
  private val store = mutable.Map[UUID, Suspect]()

  def seed(s: Suspect): Unit = store.put(s.id, s)

  override def findById(id: UUID): Option[Suspect] =
    store.get(id).filter(_.state == EntityState.ACTIVE)

  override def create(suspect: Suspect): Suspect =
    store.put(suspect.id, suspect)
    suspect

  override def update(suspect: Suspect): Suspect =
    store.put(suspect.id, suspect)
    suspect

  override def findAll(filter: SuspectFilter, page: PageRequest): PageResult[Suspect] =
    val filtered = store.values.filter(_.state == EntityState.ACTIVE).filter { s =>
      val matchesCase = filter.caseId.forall(_ == s.caseId)
      val matchesStatus = filter.status.forall(_ == s.status)
      val matchesQ = filter.q.forall(term => s.fullName.toLowerCase.contains(term.toLowerCase) || s.alias.exists(_.toLowerCase.contains(term.toLowerCase)))
      matchesCase && matchesStatus && matchesQ
    }.toList.sortBy(_.createdAt).reverse

    val total = filtered.size.toLong
    val offset = (page.page - 1) * page.limit
    val data = filtered.slice(offset, offset + page.limit)
    val totalPages = if total == 0L then 0 else math.ceil(total.toDouble / page.limit).toInt
    PageResult(data, PageMeta(total, page.page, page.limit, totalPages))

  override def deactivateAndUnlinkEvidences(id: UUID): Boolean =
    store.get(id) match
      case Some(s) if s.state == EntityState.ACTIVE =>
        store.put(id, s.copy(state = EntityState.INACTIVE))
        true
      case _ => false

class InMemoryEvidenceRepository extends EvidenceRepository:
  private val store = mutable.Map[UUID, Evidence]()

  def seed(e: Evidence): Unit = store.put(e.id, e)

  override def findById(id: UUID): Option[Evidence] =
    store.get(id).filter(_.state == EntityState.ACTIVE)

  override def create(evidence: Evidence): Evidence =
    store.put(evidence.id, evidence)
    evidence

  override def update(evidence: Evidence): Evidence =
    store.put(evidence.id, evidence)
    evidence

  override def findAll(filter: EvidenceFilter, page: PageRequest): PageResult[Evidence] =
    val filtered = store.values.filter(_.state == EntityState.ACTIVE).filter { e =>
      val matchesCase = filter.caseId.forall(_ == e.caseId)
      val matchesSuspect = filter.suspectId.forall(sid => e.suspectId.contains(sid))
      val matchesType = filter.typeFilter.forall(_ == e.`type`)
      val matchesQ = filter.q.forall(term => e.title.toLowerCase.contains(term.toLowerCase) || e.description.toLowerCase.contains(term.toLowerCase))
      matchesCase && matchesSuspect && matchesType && matchesQ
    }.toList.sortBy(_.createdAt).reverse

    val total = filtered.size.toLong
    val offset = (page.page - 1) * page.limit
    val data = filtered.slice(offset, offset + page.limit)
    val totalPages = if total == 0L then 0 else math.ceil(total.toDouble / page.limit).toInt
    PageResult(data, PageMeta(total, page.page, page.limit, totalPages))

  override def deactivate(id: UUID): Boolean =
    store.get(id) match
      case Some(e) if e.state == EntityState.ACTIVE =>
        store.put(id, e.copy(state = EntityState.INACTIVE))
        true
      case _ => false

class InMemoryAnalyticsRepository extends AnalyticsRepository:
  var cases: List[edu.investigacion.analytics.CaseRec] = Nil
  var suspects: List[edu.investigacion.analytics.SuspectRec] = Nil
  var evidences: List[edu.investigacion.analytics.EvidenceRec] = Nil

  override def fetchActiveRecords(): (List[edu.investigacion.analytics.CaseRec], List[edu.investigacion.analytics.SuspectRec], List[edu.investigacion.analytics.EvidenceRec]) =
    (cases, suspects, evidences)

