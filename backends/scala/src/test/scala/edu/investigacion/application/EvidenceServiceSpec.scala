package edu.investigacion.application

import java.time.Instant
import java.util.UUID
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import edu.investigacion.domain.*
import edu.investigacion.doubles.{InMemoryCaseRepository, InMemoryEvidenceRepository, InMemorySuspectRepository}

class EvidenceServiceSpec extends AnyFlatSpec with Matchers:

  val user1 = User(UUID.randomUUID(), "User1", "u1@test.com", "h", Role.USER, EntityState.ACTIVE, Instant.now(), Instant.now())

  "EvidenceService.createEvidence" should "successfully create evidence linked to same case suspect" in {
    val caseRepo = new InMemoryCaseRepository()
    val suspectRepo = new InMemorySuspectRepository()
    val evidenceRepo = new InMemoryEvidenceRepository()
    val service = new EvidenceService(evidenceRepo, caseRepo, suspectRepo)

    val c = caseRepo.create(Case(UUID.randomUUID(), 1L, "Caso 1", "Desc", "Loc", Instant.now(), CaseStatus.OPEN, None, EntityState.ACTIVE, user1.id, Instant.now(), Instant.now()))
    val s = suspectRepo.create(Suspect(UUID.randomUUID(), c.id, "Sospechoso", None, None, 50, SuspectStatus.PERSON_OF_INTEREST, None, EntityState.ACTIVE, Instant.now(), Instant.now()))

    val input = EvidenceInput(Some(c.id.toString), Some(s.id.toString), "Arma", "Pistola 9mm", "PHYSICAL", "2026-02-01T00:00:00.000Z", 5, None)
    val res = service.createEvidence(user1, input)
    res.isRight shouldBe true
    val ev = res.toOption.get
    ev.title shouldBe "Arma"
    ev.suspectId shouldBe Some(s.id)
    ev.caseId shouldBe c.id
  }

  it should "return 400 ValidationError when suspect belongs to a DIFFERENT case" in {
    val caseRepo = new InMemoryCaseRepository()
    val suspectRepo = new InMemorySuspectRepository()
    val evidenceRepo = new InMemoryEvidenceRepository()
    val service = new EvidenceService(evidenceRepo, caseRepo, suspectRepo)

    val c1 = caseRepo.create(Case(UUID.randomUUID(), 1L, "Caso 1", "Desc", "Loc", Instant.now(), CaseStatus.OPEN, None, EntityState.ACTIVE, user1.id, Instant.now(), Instant.now()))
    val c2 = caseRepo.create(Case(UUID.randomUUID(), 2L, "Caso 2", "Desc", "Loc", Instant.now(), CaseStatus.OPEN, None, EntityState.ACTIVE, user1.id, Instant.now(), Instant.now()))
    val sOfC2 = suspectRepo.create(Suspect(UUID.randomUUID(), c2.id, "Sospechoso de C2", None, None, 50, SuspectStatus.PERSON_OF_INTEREST, None, EntityState.ACTIVE, Instant.now(), Instant.now()))

    // Trying to link suspect of C2 into case C1
    val input = EvidenceInput(Some(c1.id.toString), Some(sOfC2.id.toString), "Arma", "Pistola 9mm", "PHYSICAL", "2026-02-01T00:00:00.000Z", 5, None)
    val res = service.createEvidence(user1, input)
    res.isLeft shouldBe true
    res.left.toOption.get.statusCode shouldBe 400
  }

  it should "return 409 ConflictError when creating evidence in a CLOSED case" in {
    val caseRepo = new InMemoryCaseRepository()
    val suspectRepo = new InMemorySuspectRepository()
    val evidenceRepo = new InMemoryEvidenceRepository()
    val service = new EvidenceService(evidenceRepo, caseRepo, suspectRepo)

    val closedCase = caseRepo.create(Case(UUID.randomUUID(), 1L, "Caso Cerrado", "Desc", "Loc", Instant.now(), CaseStatus.CLOSED, None, EntityState.ACTIVE, user1.id, Instant.now(), Instant.now()))

    val input = EvidenceInput(Some(closedCase.id.toString), None, "Documento", "Contrato firmado", "DOCUMENT", "2026-02-01T00:00:00.000Z", 3, None)
    val res = service.createEvidence(user1, input)
    res.isLeft shouldBe true
    res.left.toOption.get.statusCode shouldBe 409
  }
