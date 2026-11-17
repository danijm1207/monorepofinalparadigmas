package edu.investigacion.application

import java.time.Instant
import java.util.UUID
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import edu.investigacion.domain.*
import edu.investigacion.doubles.InMemoryCaseRepository

class CaseServiceSpec extends AnyFlatSpec with Matchers:

  val adminUser = User(UUID.randomUUID(), "Admin", "admin@test.com", "h", Role.ADMIN, EntityState.ACTIVE, Instant.now(), Instant.now())
  val user1 = User(UUID.randomUUID(), "User1", "user1@test.com", "h", Role.USER, EntityState.ACTIVE, Instant.now(), Instant.now())
  val user2 = User(UUID.randomUUID(), "User2", "user2@test.com", "h", Role.USER, EntityState.ACTIVE, Instant.now(), Instant.now())

  "CaseService.createCase" should "create a case with owner set to current user" in {
    val repo = new InMemoryCaseRepository()
    val service = new CaseService(repo)
    val input = CaseInput("Caso de Homicidio", "Investigación en desarrollo", "Envigado", "2026-05-10T10:00:00.000Z")

    val res = service.createCase(user1, input)
    res.isRight shouldBe true
    val c = res.toOption.get
    c.title shouldBe "Caso de Homicidio"
    c.ownerId shouldBe user1.id
    c.status shouldBe CaseStatus.OPEN
  }

  "CaseService.getCaseById" should "allow owner to retrieve case" in {
    val repo = new InMemoryCaseRepository()
    val service = new CaseService(repo)
    val input = CaseInput("Caso de Homicidio", "Investigación en desarrollo", "Envigado", "2026-05-10T10:00:00.000Z")
    val created = service.createCase(user1, input).toOption.get

    val res = service.getCaseById(user1, created.id)
    res.isRight shouldBe true
    res.toOption.get.id shouldBe created.id
  }

  it should "return 404 NOT_FOUND when non-owner USER tries to access the case" in {
    val repo = new InMemoryCaseRepository()
    val service = new CaseService(repo)
    val input = CaseInput("Caso de Homicidio", "Investigación en desarrollo", "Envigado", "2026-05-10T10:00:00.000Z")
    val created = service.createCase(user1, input).toOption.get

    val res = service.getCaseById(user2, created.id)
    res.isLeft shouldBe true
    res.left.toOption.get.statusCode shouldBe 404
  }

  it should "allow ADMIN to access any case" in {
    val repo = new InMemoryCaseRepository()
    val service = new CaseService(repo)
    val input = CaseInput("Caso de Homicidio", "Investigación en desarrollo", "Envigado", "2026-05-10T10:00:00.000Z")
    val created = service.createCase(user1, input).toOption.get

    val res = service.getCaseById(adminUser, created.id)
    res.isRight shouldBe true
    res.toOption.get.id shouldBe created.id
  }

  "CaseService.deleteCase" should "deactivate case and prevent subsequent retrieval" in {
    val repo = new InMemoryCaseRepository()
    val service = new CaseService(repo)
    val input = CaseInput("Caso de Homicidio", "Investigación en desarrollo", "Envigado", "2026-05-10T10:00:00.000Z")
    val created = service.createCase(user1, input).toOption.get

    service.deleteCase(user1, created.id).isRight shouldBe true
    service.getCaseById(user1, created.id).isLeft shouldBe true
  }
