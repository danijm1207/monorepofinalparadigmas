package edu.investigacion.infrastructure

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import scala.util.Try
import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import edu.investigacion.domain.{AppError, Role}

trait PasswordHasher:
  def hash(password: String): String
  def verify(password: String, hash: String): Boolean

class BcryptPasswordHasher(cost: Int = 10) extends PasswordHasher:
  override def hash(password: String): String =
    BCrypt.withDefaults().hashToString(cost, password.toCharArray)

  override def verify(password: String, hash: String): Boolean =
    val result = BCrypt.verifyer().verify(password.toCharArray, hash)
    result.verified

trait TokenService:
  def createToken(userId: UUID, role: Role): String
  def verifyToken(token: String): Either[AppError, (UUID, Role)]

class JwtTokenService(secret: String, expiresInHours: Long) extends TokenService:
  private val algorithm = Algorithm.HMAC256(secret)
  private val verifier = JWT.require(algorithm).build()

  override def createToken(userId: UUID, role: Role): String =
    val now = Instant.now()
    val expiresAt = now.plus(expiresInHours, ChronoUnit.HOURS)

    JWT
      .create()
      .withSubject(userId.toString)
      .withClaim("role", role.toString)
      .withIssuedAt(Date.from(now))
      .withExpiresAt(Date.from(expiresAt))
      .sign(algorithm)

  override def verifyToken(token: String): Either[AppError, (UUID, Role)] =
    Try {
      val decoded = verifier.verify(token)
      val subStr = decoded.getSubject
      val roleStr = decoded.getClaim("role").asString()
      val userId = UUID.fromString(subStr)
      val role = Role.valueOf(roleStr)
      (userId, role)
    }.toEither.left.map(_ => AppError.UnauthorizedError("Token inválido o expirado"))
