package edu.investigacion.application;

import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Models.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccessPolicyTest {

    private final UUID user1Id = UUID.randomUUID();
    private final UUID user2Id = UUID.randomUUID();
    private final User regularUser = new User(user1Id, "User 1", "u1@test.com", "hash", Role.USER, State.ACTIVE, Instant.now(), Instant.now());
    private final User adminUser = new User(UUID.randomUUID(), "Admin", "admin@test.com", "hash", Role.ADMIN, State.ACTIVE, Instant.now(), Instant.now());

    @Test
    @DisplayName("USER solo accede a casos donde es el propietario")
    void userCanAccessOnlyOwnCases() {
        assertThat(AccessPolicy.canAccessCase(regularUser, user1Id)).isTrue();
        assertThat(AccessPolicy.canAccessCase(regularUser, user2Id)).isFalse();
    }

    @Test
    @DisplayName("ADMIN puede acceder a casos de cualquier propietario")
    void adminCanAccessAnyCase() {
        assertThat(AccessPolicy.canAccessCase(adminUser, user1Id)).isTrue();
        assertThat(AccessPolicy.canAccessCase(adminUser, user2Id)).isTrue();
    }

    @Test
    @DisplayName("isAdmin identifica correctamente el rol")
    void isAdminIdentifiesRole() {
        assertThat(AccessPolicy.isAdmin(adminUser)).isTrue();
        assertThat(AccessPolicy.isAdmin(regularUser)).isFalse();
    }
}
