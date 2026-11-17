package edu.investigacion.application

import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.UUID
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import edu.investigacion.domain.*
import edu.investigacion.doubles.InMemoryUserRepository
import edu.investigacion.infrastructure.AppConfig

class UserServiceSpec extends AnyFlatSpec with Matchers:

  val admin = User(UUID.randomUUID(), "Admin", "admin@test.com", "h", Role.ADMIN, EntityState.ACTIVE, Instant.now(), Instant.now())
  val target = User(UUID.randomUUID(), "Investigador", "inv@test.com", "h", Role.USER, EntityState.ACTIVE, Instant.now(), Instant.now())

  "UserService.deactivateUser" should "prevent admin from deactivating himself returning 409 Conflict" in {
    val repo = new InMemoryUserRepository()
    repo.seed(admin)
    val service = new UserService(repo)

    val res = service.deactivateUser(admin, admin.id)
    res.isLeft shouldBe true
    res.left.toOption.get.statusCode shouldBe 409
  }

  it should "allow admin to deactivate another user" in {
    val repo = new InMemoryUserRepository()
    repo.seed(admin)
    repo.seed(target)
    val service = new UserService(repo)

    val res = service.deactivateUser(admin, target.id)
    res.isRight shouldBe true
    repo.findById(target.id) shouldBe None
  }

class UploadServiceSpec extends AnyFlatSpec with Matchers:

  val config = AppConfig(
    port = 8080,
    databaseUrl = "jdbc:postgresql://localhost/test",
    databaseUser = "u",
    databasePassword = "p",
    dbPoolSize = 5,
    jwtSecret = "secret",
    jwtExpiresInHours = 24L,
    corsOrigins = Nil,
    uploadDir = sys.props("java.io.tmpdir") + "/uploads_test",
    publicBaseUrl = "http://localhost:8082"
  )
  val uploadService = new UploadService(config)

  "UploadService.detectExtension" should "correctly identify JPEG magic bytes" in {
    val jpegBytes = Array(0xFF.toByte, 0xD8.toByte, 0xFF.toByte, 0xE0.toByte)
    uploadService.detectExtension(jpegBytes) shouldBe Some("jpg")
  }

  it should "correctly identify PNG magic bytes" in {
    val pngBytes = Array(0x89.toByte, 0x50.toByte, 0x4E.toByte, 0x47.toByte, 0x0D.toByte, 0x0A.toByte, 0x1A.toByte, 0x0A.toByte)
    uploadService.detectExtension(pngBytes) shouldBe Some("png")
  }

  it should "correctly identify WEBP magic bytes" in {
    val webpBytes = ("RIFF" + "xxxx" + "WEBP").getBytes("US-ASCII")
    uploadService.detectExtension(webpBytes) shouldBe Some("webp")
  }

  it should "reject non-image or executable files" in {
    val exeBytes = Array('M'.toByte, 'Z'.toByte, 0x00.toByte, 0x00.toByte)
    uploadService.detectExtension(exeBytes) shouldBe None
  }

  "UploadService.uploadFile" should "reject files exceeding 5MB returning 413" in {
    val largeBytes = new Array[Byte](5 * 1024 * 1024 + 10)
    val stream = new ByteArrayInputStream(largeBytes)
    val res = uploadService.uploadFile(Some("cases"), stream, largeBytes.length)
    res.isLeft shouldBe true
    res.left.toOption.get.statusCode shouldBe 413
  }

  it should "reject disallowed folder names" in {
    val jpegBytes = Array(0xFF.toByte, 0xD8.toByte, 0xFF.toByte, 0xE0.toByte)
    val stream = new ByteArrayInputStream(jpegBytes)
    val res = uploadService.uploadFile(Some("malicious_folder"), stream, jpegBytes.length)
    res.isLeft shouldBe true
    res.left.toOption.get.statusCode shouldBe 400
  }
