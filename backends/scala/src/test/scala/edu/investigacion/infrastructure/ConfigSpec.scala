package edu.investigacion.infrastructure

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfigSpec extends AnyFlatSpec with Matchers:

  "AppConfig" should "hold correct configuration values" in {
    val cfg = AppConfig(
      port = 8080,
      databaseUrl = "jdbc:postgresql://localhost:5432/test",
      databaseUser = "postgres",
      databasePassword = "password",
      dbPoolSize = 5,
      jwtSecret = "secret",
      jwtExpiresInHours = 12L,
      corsOrigins = List("http://localhost:3000"),
      uploadDir = "/tmp/uploads",
      publicBaseUrl = "http://localhost:8080"
    )

    cfg.port shouldBe 8080
    cfg.databaseUser shouldBe "postgres"
    cfg.corsOrigins should contain("http://localhost:3000")
  }
