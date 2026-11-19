package edu.investigacion.domain;

import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Models.User;

import java.util.Optional;
import java.util.UUID;

public final class Security {
    private Security() {}

    public interface PasswordHasher {
        String hash(String rawPassword);
        boolean verify(String rawPassword, String hash);
    }

    public record DecodedToken(UUID userId, Role role) {}

    public interface TokenService {
        String generateToken(User user);
        Optional<DecodedToken> verify(String token);
    }
}
