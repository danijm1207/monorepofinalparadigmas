package edu.investigacion.application;

import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Models.User;

import java.util.UUID;

public final class AccessPolicy {
    private AccessPolicy() {}

    public static boolean canAccessCase(User user, UUID caseOwnerId) {
        if (user.role() == Role.ADMIN) {
            return true;
        }
        return user.id().equals(caseOwnerId);
    }

    public static boolean isAdmin(User user) {
        return user.role() == Role.ADMIN;
    }
}
