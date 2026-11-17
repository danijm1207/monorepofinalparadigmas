package edu.investigacion.application

import java.time.Instant
import java.util.UUID
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import edu.investigacion.domain.*

class AccessPolicySpec extends AnyFlatSpec with Matchers:

  val adminUser = User(
    id = UUID.randomUUID(),
    name = "Admin",
    email = "admin@test.com",
    passwordHash = "hash",
    role = Role.ADMIN,
    state = EntityState.ACTIVE,
    createdAt = Instant.now(),
    updatedAt = Instant.now()
  )

  val ownerUser = User(
    id = UUID.randomUUID(),
    name = "Owner",
    email = "owner@test.com",
    passwordHash = "hash",
    role = Role.USER,
    state = EntityState.ACTIVE,
    createdAt = Instant.now(),
    updatedAt = Instant.now()
  )

  val strangerUser = User(
    id = UUID.randomUUID(),
    name = "Stranger",
    email = "stranger@test.com",
    passwordHash = "hash",
    role = Role.USER,
    state = EntityState.ACTIVE,
    createdAt = Instant.now(),
    updatedAt = Instant.now()
  )

  val testCase = Case(
    id = UUID.randomUUID(),
    caseNumber = 1L,
    title = "Caso de prueba",
    description = "Descripción del caso de prueba",
    location = "Lugar",
    incidentDate = Instant.now(),
    status = CaseStatus.OPEN,
    imageUrl = None,
    state = EntityState.ACTIVE,
    ownerId = ownerUser.id,
    createdAt = Instant.now(),
    updatedAt = Instant.now()
  )

  "AccessPolicy.canAccessCase" should "allow ADMIN to access any case" in {
    AccessPolicy.canAccessCase(adminUser, testCase).isRight shouldBe true
  }

  it should "allow USER to access their own case" in {
    AccessPolicy.canAccessCase(ownerUser, testCase).isRight shouldBe true
  }

  it should "deny USER access to a case they do not own, returning 404 NOT_FOUND" in {
    val res = AccessPolicy.canAccessCase(strangerUser, testCase)
    res.isLeft shouldBe true
    res.left.toOption.get.statusCode shouldBe 404
  }
