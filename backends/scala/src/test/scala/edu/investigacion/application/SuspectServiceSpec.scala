package edu.investigacion.application

import java.time.Instant
import java.util.UUID
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import edu.investigacion.domain.*
import edu.investigacion.doubles.{InMemoryCaseRepository, InMemorySuspectRepository}

class SuspectServiceSpec extends AnyFlatSpec with Matchers:

  val user1 = User(UUID.randomUUID(), "User1", "u1@test.com", "h", Role.USER, EntityState.ACTIVE, Instant.now(), Instant.now())
  val user2 = User(UUID.randomUUID(), "User2", "u2@test.com", "h", Role.USER, EntityState.ACTIVE, Instant.now(), Instant.now())

  "SuspectService.createSuspect" should "create a suspect associated with an OPEN case" in {
    val caseRepo = new InMemoryCaseRepository()
    val suspectRepo = new InMemorySuspectRepository()
    val suspectService = new SuspectService(suspectRepo, caseRepo)

    val c = caseRepo.create(Case(UUID.randomUUID(), 1L, "Titulo", "Desc", "Loc", Instant.now(), CaseStatus.OPEN, None, EntityState.ACTIVE, user1.id, Instant.now(), Instant.now()))

    val input = SuspectInput(Some(c.id.toString), "Carlos Ramírez", Some("El Charly"), Some("Sospechoso clave"), Some(75), Some("PERSON_OF_INTEREST"), None)
    val res = suspectService.createSuspect(user1, input)
    res.isRight shouldBe true
    val suspect = res.toOption.get
    suspect.fullName shouldBe "Carlos Ramírez"
    suspect.suspicionLevel shouldBe 75
    suspect.caseId shouldBe c.id
  }

  it should "return ConflictError (409) when creating a suspect in a CLOSED case" in {
    val caseRepo = new InMemoryCaseRepository()
    val suspectRepo = new InMemorySuspectRepository()
    val suspectService = new SuspectService(suspectRepo, caseRepo)

    val c = caseRepo.create(Case(UUID.randomUUID(), 1L, "Titulo", "Desc", "Loc", Instant.now(), CaseStatus.CLOSED, None, EntityState.ACTIVE, user1.id, Instant.now(), Instant.now()))

    val input = SuspectInput(Some(c.id.toString), "Carlos Ramírez", None, None, Some(50), None, None)
    val res = suspectService.createSuspect(user1, input)
    res.isLeft shouldBe true
    res.left.toOption.get.statusCode shouldBe 409
  }

  it should "return NotFoundError (404) when a user tries to add suspect to a case they do not own" in {
    val caseRepo = new InMemoryCaseRepository()
    val suspectRepo = new InMemorySuspectRepository()
    val suspectService = new SuspectService(suspectRepo, caseRepo)

    val c = caseRepo.create(Case(UUID.randomUUID(), 1L, "Titulo", "Desc", "Loc", Instant.now(), CaseStatus.OPEN, None, EntityState.ACTIVE, user1.id, Instant.now(), Instant.now()))

    val input = SuspectInput(Some(c.id.toString), "Carlos Ramírez", None, None, Some(50), None, None)
    val res = suspectService.createSuspect(user2, input)
    res.isLeft shouldBe true
    res.left.toOption.get.statusCode shouldBe 404
  }
