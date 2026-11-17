package edu.investigacion.application

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ValidatorsSpec extends AnyFlatSpec with Matchers:

  "Validators.validateRegister" should "accept a valid user registration" in {
    val input = RegisterInput("Juan Pérez", "juan@test.com", "Password123")
    val res = Validators.validateRegister(input)
    res.isRight shouldBe true
    val (name, email, pwd) = res.toOption.get
    name shouldBe "Juan Pérez"
    email shouldBe "juan@test.com"
    pwd shouldBe "Password123"
  }

  it should "accumulate multiple errors when name, email and password are all invalid" in {
    val input = RegisterInput("A", "not-an-email", "short")
    val res = Validators.validateRegister(input)
    res.isLeft shouldBe true
    val err = res.left.toOption.get
    val fields = err.details.map(_.field).toSet
    fields should contain("name")
    fields should contain("email")
    fields should contain("password")
  }

  it should "reject password with only letters or only numbers" in {
    val input1 = RegisterInput("Usuario Test", "u1@test.com", "sololetraspass")
    val res1 = Validators.validateRegister(input1)
    res1.isLeft shouldBe true

    val input2 = RegisterInput("Usuario Test", "u2@test.com", "1234567890")
    val res2 = Validators.validateRegister(input2)
    res2.isLeft shouldBe true
  }

  "Validators.validateCase" should "accept a valid case" in {
    val input = CaseInput("Robo agravado", "Descripción suficiente del caso", "Medellín Centro", "2026-10-01T12:00:00.000Z")
    val res = Validators.validateCase(input)
    res.isRight shouldBe true
  }

  it should "reject short title, short description and invalid date" in {
    val input = CaseInput("ab", "corta", "ab", "invalid-date")
    val res = Validators.validateCase(input)
    res.isLeft shouldBe true
    val fields = res.left.toOption.get.details.map(_.field).toSet
    fields should contain("title")
    fields should contain("description")
    fields should contain("location")
    fields should contain("incidentDate")
  }

  "Validators.validateEvidence" should "reject future collectedAt date" in {
    val futureDate = "2099-12-31T23:59:59.000Z"
    val input = EvidenceInput(Some("00000000-0000-4000-8000-000000000001"), None, "Huella", "Huella dactilar encontrada", "FORENSIC", futureDate, 4)
    val res = Validators.validateEvidence(input, requireCaseId = true)
    res.isLeft shouldBe true
    res.left.toOption.get.details.exists(_.field == "collectedAt") shouldBe true
  }

  it should "reject invalid relevance out of 1..5 range" in {
    val input = EvidenceInput(Some("00000000-0000-4000-8000-000000000001"), None, "Huella", "Huella dactilar encontrada", "FORENSIC", "2026-01-01T00:00:00.000Z", 6)
    val res = Validators.validateEvidence(input, requireCaseId = true)
    res.isLeft shouldBe true
    res.left.toOption.get.details.exists(_.field == "relevance") shouldBe true
  }

  "Validators.validateBenchmark" should "accept valid scenario and seed" in {
    val input = BenchmarkInput("T", Some(42L))
    val res = Validators.validateBenchmark(input)
    res.isRight shouldBe true
    res.toOption.get shouldBe ("T", 42L)
  }

  it should "reject unknown scenario and negative seed" in {
    val input = BenchmarkInput("Z", Some(-1L))
    val res = Validators.validateBenchmark(input)
    res.isLeft shouldBe true
    val fields = res.left.toOption.get.details.map(_.field).toSet
    fields should contain("scenario")
    fields should contain("seed")
  }

  "Validators.validatePageRequest" should "validate page and limit within boundaries" in {
    val p1 = Validators.validatePageRequest(Some("-5"), Some("999"))
    p1.isLeft shouldBe true
    val fields1 = p1.left.toOption.get.details.map(_.field).toSet
    fields1 should contain("page")
    fields1 should contain("limit")

    val p2 = Validators.validatePageRequest(None, None)
    p2 shouldBe Right(edu.investigacion.domain.PageRequest(1, 10))

    val p3 = Validators.validatePageRequest(Some("2"), Some("25"))
    p3 shouldBe Right(edu.investigacion.domain.PageRequest(2, 25))

    val p4 = Validators.validatePageRequest(Some("0"), Some("10"))
    p4.isLeft shouldBe true

    val p5 = Validators.validatePageRequest(Some("1"), Some("51"))
    p5.isLeft shouldBe true

    val p6 = Validators.validatePageRequest(Some("abc"), Some("10"))
    p6.isLeft shouldBe true
  }
