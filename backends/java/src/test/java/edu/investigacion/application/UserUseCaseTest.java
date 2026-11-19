package edu.investigacion.application;

import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Models.Page;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Result;
import edu.investigacion.fakes.FakeRepositories.FakeUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserUseCaseTest {

    private FakeUserRepository userRepo;
    private UserUseCase userUseCase;

    private final UUID adminId = UUID.randomUUID();
    private final UUID regularUserId = UUID.randomUUID();
    private final User admin = new User(adminId, "Administrador", "admin@test.com", "hash", Role.ADMIN, State.ACTIVE, Instant.now(), Instant.now());
    private final User regularUser = new User(regularUserId, "Usuario", "user@test.com", "hash", Role.USER, State.ACTIVE, Instant.now(), Instant.now());

    @BeforeEach
    void setUp() {
        userRepo = new FakeUserRepository();
        userRepo.save(admin);
        userRepo.save(regularUser);
        userUseCase = new UserUseCase(userRepo);
    }

    @Test
    @DisplayName("USER no puede listar usuarios (403 FORBIDDEN)")
    void userCannotListUsers() {
        var res = userUseCase.list(regularUser, 1, 10, Optional.empty(), Optional.empty());
        assertThat(res.isFailure()).isTrue();
        var err = ((Result.Failure<Page<User>>) res).error();
        assertThat(err).isInstanceOf(AppError.Forbidden.class);
    }

    @Test
    @DisplayName("ADMIN puede listar usuarios exitosamente")
    void adminCanListUsers() {
        var res = userUseCase.list(admin, 1, 10, Optional.empty(), Optional.empty());
        assertThat(res.isSuccess()).isTrue();
        Page<User> page = ((Result.Success<Page<User>>) res).value();
        assertThat(page.items()).hasSize(2);
    }

    @Test
    @DisplayName("ADMIN no puede desactivarse a sí mismo (409 CONFLICT)")
    void adminCannotDeactivateSelf() {
        var res = userUseCase.deactivate(admin, adminId);
        assertThat(res.isFailure()).isTrue();
        var err = ((Result.Failure<Void>) res).error();
        assertThat(err).isInstanceOf(AppError.Conflict.class);
    }

    @Test
    @DisplayName("ADMIN desactiva a otro usuario exitosamente")
    void adminCanDeactivateOtherUser() {
        var res = userUseCase.deactivate(admin, regularUserId);
        assertThat(res.isSuccess()).isTrue();
        assertThat(userRepo.findById(regularUserId)).isEmpty();
    }
}
