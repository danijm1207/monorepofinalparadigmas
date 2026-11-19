package edu.investigacion.infrastructure;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Security.DecodedToken;
import edu.investigacion.domain.Security.TokenService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

public final class JwtTokenService implements TokenService {
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final int expiresInHours;

    public JwtTokenService(String secret, int expiresInHours) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(this.algorithm).build();
        this.expiresInHours = expiresInHours;
    }

    @Override
    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiresInHours, ChronoUnit.HOURS);

        return JWT.create()
            .withSubject(user.id().toString())
            .withClaim("role", user.role().name())
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .sign(algorithm);
    }

    @Override
    public Optional<DecodedToken> verify(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            DecodedJWT jwt = verifier.verify(token);
            UUID userId = UUID.fromString(jwt.getSubject());
            String roleStr = jwt.getClaim("role").asString();
            Role role = Role.valueOf(roleStr);
            return Optional.of(new DecodedToken(userId, role));
        } catch (JWTVerificationException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
