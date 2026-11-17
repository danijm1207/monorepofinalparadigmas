package edu.investigacion.infrastructure;

import at.favre.lib.crypto.bcrypt.BCrypt;
import edu.investigacion.domain.Security.PasswordHasher;

public final class BcryptPasswordHasher implements PasswordHasher {
    private static final int COST = 10;

    @Override
    public String hash(String rawPassword) {
        return BCrypt.withDefaults().hashToString(COST, rawPassword.toCharArray());
    }

    @Override
    public boolean verify(String rawPassword, String hash) {
        if (hash == null || rawPassword == null) return false;
        return BCrypt.verifyer().verify(rawPassword.toCharArray(), hash.toCharArray()).verified;
    }
}
