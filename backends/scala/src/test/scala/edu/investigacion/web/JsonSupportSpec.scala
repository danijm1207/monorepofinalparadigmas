package edu.investigacion.web

import java.time.Instant
import java.util.UUID
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import edu.investigacion.domain.*
import edu.investigacion.application.*

class JsonSupportSpec extends AnyFlatSpec with Matchers:

  val isoRegex = """^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$""".r

  "JsonSupport.formatInstant" should "format timestamps with exactly 3 millisecond digits and Z in UTC" in {
    val instant = Instant.parse("2026-10-01T00:00:00Z")
    val formatted = JsonSupport.formatInstant(instant)
    formatted shouldBe "2026-10-01T00:00:00.000Z"
    isoRegex.matches(formatted) shouldBe true
  }

  "JsonSupport.userToJson" should "never expose passwordHash or credentials" in {
    val user = User(
      id = UUID.randomUUID(),
      name = "Agente Fox",
      email = "fox@investigacion.test",
      passwordHash = "$2b$10$unhashsecretoquenoespermitido",
      role = Role.USER,
      state = EntityState.ACTIVE,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )

    val json = JsonSupport.userToJson(user)
    json.has("passwordHash") shouldBe false
    json.has("password_hash") shouldBe false
    json.has("password") shouldBe false
    json.get("name").asText() shouldBe "Agente Fox"
    json.get("role").asText() shouldBe "USER"
    isoRegex.matches(json.get("createdAt").asText()) shouldBe true
  }

  "JsonSupport.errorResponse" should "format error with standard envelope" in {
    val err = AppError.ValidationError("Datos inválidos", List(FieldError("email", "Formato inválido")))
    val json = JsonSupport.errorResponse(err)
    json.has("error") shouldBe true
    val errNode = json.get("error")
    errNode.get("code").asText() shouldBe "VALIDATION_ERROR"
    errNode.get("message").asText() shouldBe "Datos inválidos"
    errNode.has("details") shouldBe true
    errNode.get("details").size() shouldBe 1
  }

  "JsonSupport.caseToJson" should "serialize case attributes in consistent structure" in {
    val c = Case(
      id = UUID.randomUUID(),
      caseNumber = 42L,
      title = "Robo a banco",
      description = "Asalto a mano armada",
      location = "Calle 10",
      incidentDate = Instant.now(),
      status = CaseStatus.OPEN,
      imageUrl = Some("http://localhost:8082/uploads/cases/test.png"),
      state = EntityState.ACTIVE,
      ownerId = UUID.randomUUID(),
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    val json = JsonSupport.caseToJson(c)
    json.get("caseNumber").asLong() shouldBe 42L
    json.get("status").asText() shouldBe "OPEN"
    json.get("imageUrl").asText() shouldBe "http://localhost:8082/uploads/cases/test.png"
  }

  "JsonSupport.suspectToJson" should "serialize suspect attributes" in {
    val s = Suspect(
      id = UUID.randomUUID(),
      caseId = UUID.randomUUID(),
      fullName = "John Doe",
      alias = Some("Ghost"),
      description = Some("Unknown suspect"),
      suspicionLevel = 75,
      status = SuspectStatus.PERSON_OF_INTEREST,
      photoUrl = None,
      state = EntityState.ACTIVE,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    val json = JsonSupport.suspectToJson(s)
    json.get("fullName").asText() shouldBe "John Doe"
    json.get("alias").asText() shouldBe "Ghost"
    json.get("suspicionLevel").asInt() shouldBe 75
    json.get("photoUrl").isNull shouldBe true
  }

  "JsonSupport.evidenceToJson" should "serialize evidence attributes" in {
    val e = Evidence(
      id = UUID.randomUUID(),
      caseId = UUID.randomUUID(),
      suspectId = None,
      title = "Arma blanca",
      description = "Cuchillo hallado en la escena",
      `type` = EvidenceType.PHYSICAL,
      collectedAt = Instant.now(),
      relevance = 5,
      imageUrl = None,
      state = EntityState.ACTIVE,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    val json = JsonSupport.evidenceToJson(e)
    json.get("title").asText() shouldBe "Arma blanca"
    json.get("relevance").asInt() shouldBe 5
    json.get("type").asText() shouldBe "PHYSICAL"
    json.get("suspectId").isNull shouldBe true
  }

  "JsonSupport.pagedResource" should "serialize pagination metadata and elements" in {
    val paged = PageResult[String](
      data = List("item1", "item2"),
      pagination = PageMeta(total = 2, currentPage = 1, limit = 10, totalPages = 1)
    )
    val json = JsonSupport.pagedResource[String](paged, str => {
      val n = JsonSupport.mapper.createObjectNode()
      n.put("name", str)
      n
    })
    json.has("data") shouldBe true
    json.get("data").size() shouldBe 2
    json.get("pagination").get("total").asLong() shouldBe 2L
  }

  "JsonSupport.authResponse" should "serialize user and token" in {
    val user = User(
      id = UUID.randomUUID(),
      name = "Admin",
      email = "admin@test.com",
      passwordHash = "hash",
      role = Role.ADMIN,
      state = EntityState.ACTIVE,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    val json = JsonSupport.authResponse(user, "token123")
    json.get("token").asText() shouldBe "token123"
    json.has("user") shouldBe true
  }

  "JsonSupport.uploadResponse" should "serialize upload results" in {
    val res = UploadResult("http://localhost:8082/uploads/test.png", "/data/uploads/test.png")
    val json = JsonSupport.uploadResponse(res)
    json.get("data").get("url").asText() shouldBe "http://localhost:8082/uploads/test.png"
    json.get("data").get("path").asText() shouldBe "/data/uploads/test.png"
  }

  "JsonSupport.benchmarkResponse" should "serialize benchmark result" in {
    val res = BenchmarkResult(
      scenario = "T",
      seed = 42L,
      counts = Map("cases" -> 10, "suspects" -> 20, "evidences" -> 50),
      processedRecords = 80,
      timings = BenchmarkTimings(1000L, 2000L),
      jvm = JvmMemory(1000000L, 2000000L),
      checksum = "abc123",
      report = Some(List("LINE1", "LINE2"))
    )
    val json = JsonSupport.benchmarkResponse(res)
    json.get("data").get("scenario").asText() shouldBe "T"
    json.get("data").get("checksum").asText() shouldBe "abc123"
  }

  "JsonSupport.overviewResponse" should "serialize overview metrics" in {
    val res = OverviewResult(
      processedRecords = 100,
      timings = OverviewTimings(5000L, 10000L),
      checksum = "def456",
      report = List("R1", "R2")
    )
    val json = JsonSupport.overviewResponse(res)
    json.get("data").get("checksum").asText() shouldBe "def456"
    json.get("data").get("processedRecords").asLong() shouldBe 100L
  }
