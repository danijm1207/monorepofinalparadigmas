package edu.investigacion.infrastructure

import java.util.UUID
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import edu.investigacion.domain.Role

class SecurityServicesSpec extends AnyFlatSpec with Matchers:

  val hasher = new BcryptPasswordHasher(cost = 10)
  val tokenService = new JwtTokenService("una_clave_secreta_super_segura_de_al_menos_256_bits_de_longitud_12345", 24L)

  "BcryptPasswordHasher" should "hash and successfully verify matching password" in {
    val pwd = "MiPassword123"
    val hash = hasher.hash(pwd)
    hash should not be empty
    hasher.verify(pwd, hash) shouldBe true
  }

  it should "fail verification for wrong password" in {
    val hash = hasher.hash("MiPassword123")
    hasher.verify("OtroPassword456", hash) shouldBe false
  }

  "JwtTokenService" should "create a signed token and verify it extracting userId and role" in {
    val userId = UUID.randomUUID()
    val role = Role.ADMIN
    val token = tokenService.createToken(userId, role)
    token should not be empty

    val decoded = tokenService.verifyToken(token)
    decoded.isRight shouldBe true
    val (extractedId, extractedRole) = decoded.toOption.get
    extractedId shouldBe userId
    extractedRole shouldBe role
  }

  it should "reject tampered token with UnauthorizedError" in {
    val token = tokenService.createToken(UUID.randomUUID(), Role.USER)
    val tampered = token + "corrupted"
    val res = tokenService.verifyToken(tampered)
    res.isLeft shouldBe true
    res.left.toOption.get.statusCode shouldBe 401
  }
