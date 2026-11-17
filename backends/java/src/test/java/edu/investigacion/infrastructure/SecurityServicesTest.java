package edu.investigacion.infrastructure;

import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Models.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityServicesTest {

    @Test
    @DisplayName("Bcrypt hashea y verifica contraseñas correctamente")
    void bcryptHashingAndVerification() {
        BcryptPasswordHasher hasher = new BcryptPasswordHasher();
        String raw = "Password123";
        String hash = hasher.hash(raw);

        assertThat(hash).isNotBlank();
        assertThat(hasher.verify(raw, hash)).isTrue();
        assertThat(hasher.verify("WrongPass", hash)).isFalse();
    }

    @Test
    @DisplayName("JWT genera y valida tokens con claims de usuario")
    void jwtTokenGenerationAndVerification() {
        JwtTokenService jwt = new JwtTokenService("test_secret_must_be_very_long_at_least_256_bits", 24);
        User user = new User(UUID.randomUUID(), "Investigador", "user@test.com", "hash", Role.USER, State.ACTIVE, Instant.now(), Instant.now());

        String token = jwt.generateToken(user);
        assertThat(token).isNotBlank();

        var decoded = jwt.verify(token);
        assertThat(decoded).isPresent();
        assertThat(decoded.get().userId()).isEqualTo(user.id());
        assertThat(decoded.get().role()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("JWT rechaza tokens manipulados o firmas inválidas")
    void jwtRejectsInvalidTokens() {
        JwtTokenService jwt = new JwtTokenService("secret1_secret1_secret1_secret1_secret1_secret1", 24);
        JwtTokenService jwtDifferent = new JwtTokenService("secret2_secret2_secret2_secret2_secret2_secret2", 24);
        User user = new User(UUID.randomUUID(), "Admin", "admin@test.com", "hash", Role.ADMIN, State.ACTIVE, Instant.now(), Instant.now());

        String token = jwt.generateToken(user);
        assertThat(jwtDifferent.verify(token)).isEmpty();
        assertThat(jwt.verify("token.falso.invalido")).isEmpty();
    }
}
