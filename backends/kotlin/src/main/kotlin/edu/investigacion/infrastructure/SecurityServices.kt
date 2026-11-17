package edu.investigacion.infrastructure

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import edu.investigacion.domain.Role
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

object PasswordHasher {
    fun hash(password: String): String {
        return BCrypt.withDefaults().hashToString(10, password.toCharArray())
    }

    fun verify(password: String, hash: String): Boolean {
        val result = BCrypt.verifyer().verify(password.toCharArray(), hash.toCharArray())
        return result.verified
    }
}

class TokenService(private val secret: String, private val expiresInHours: Long) {
    private val algorithm = Algorithm.HMAC256(secret)
    private val verifier = JWT.require(algorithm).build()

    data class TokenPayload(val userId: String, val role: Role)

    fun createToken(userId: String, role: Role): String {
        val now = Instant.now()
        val expiresAt = now.plus(expiresInHours, ChronoUnit.HOURS)
        return JWT.create()
            .withSubject(userId)
            .withClaim("role", role.name)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expiresAt))
            .sign(algorithm)
    }

    fun verifyToken(token: String): TokenPayload? {
        return try {
            val decoded = verifier.verify(token)
            val userId = decoded.subject ?: return null
            val roleName = decoded.getClaim("role").asString() ?: return null
            val role = Role.valueOf(roleName)
            TokenPayload(userId, role)
        } catch (_: JWTVerificationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
