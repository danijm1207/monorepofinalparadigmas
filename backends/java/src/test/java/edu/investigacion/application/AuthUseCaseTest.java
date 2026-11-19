package edu.investigacion.application;

import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Result;
import edu.investigacion.fakes.FakeRepositories.FakeUserRepository;
import edu.investigacion.infrastructure.BcryptPasswordHasher;
import edu.investigacion.infrastructure.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthUseCaseTest {

    private FakeUserRepository userRepo;
    private BcryptPasswordHasher hasher;
    private JwtTokenService tokenService;
    private AuthUseCase authUseCase;

    @BeforeEach
    void setUp() {
        userRepo = new FakeUserRepository();
        hasher = new BcryptPasswordHasher();
        tokenService = new JwtTokenService("secret_jwt_for_unit_testing_purposes_only_at_least_256", 24);
        authUseCase = new AuthUseCase(userRepo, hasher, tokenService);
    }

    @Test
    @DisplayName("Registro exitoso crea usuario USER y genera token JWT")
    void registerSuccess() {
        var res = authUseCase.register("Nuevo Agente", "agente@test.com", "Password123");
        assertThat(res.isSuccess()).isTrue();
        var authRes = ((Result.Success<AuthUseCase.AuthResult>) res).value();
        assertThat(authRes.user().role()).isEqualTo(Role.USER);
        assertThat(authRes.user().state()).isEqualTo(State.ACTIVE);
        assertThat(authRes.token()).isNotBlank();
    }

    @Test
    @DisplayName("Registro con email duplicado devuelve conflicto 409")
    void registerDuplicateEmailConflict() {
        authUseCase.register("Agente 1", "agente@test.com", "Password123");
        var resDup = authUseCase.register("Agente 2", "AGENTE@TEST.COM", "Password123");
        assertThat(resDup.isFailure()).isTrue();
        var err = ((Result.Failure<AuthUseCase.AuthResult>) resDup).error();
        assertThat(err).isInstanceOf(AppError.Conflict.class);
    }

    @Test
    @DisplayName("Login exitoso con credenciales correctas")
    void loginSuccess() {
        authUseCase.register("Agente", "agente@test.com", "Password123");
        var res = authUseCase.login("agente@test.com", "Password123");
        assertThat(res.isSuccess()).isTrue();
        var authRes = ((Result.Success<AuthUseCase.AuthResult>) res).value();
        assertThat(authRes.token()).isNotBlank();
    }

    @Test
    @DisplayName("Login con contraseña incorrecta devuelve Unauthorized y mensaje canónico")
    void loginWrongPassword() {
        authUseCase.register("Agente", "agente@test.com", "Password123");
        var res = authUseCase.login("agente@test.com", "PasswordEquivocada");
        assertThat(res.isFailure()).isTrue();
        var err = ((Result.Failure<AuthUseCase.AuthResult>) res).error();
        assertThat(err).isInstanceOf(AppError.Unauthorized.class);
        assertThat(((AppError.Unauthorized) err).message()).isEqualTo("Credenciales inválidas");
    }

    @Test
    @DisplayName("Login de usuario INACTIVE devuelve Unauthorized genérico")
    void loginInactiveUserFails() {
        String hash = hasher.hash("Password123");
        UUID uid = UUID.randomUUID();
        User inactive = new User(uid, "Inactivo", "inactivo@test.com", hash, Role.USER, State.INACTIVE, Instant.now(), Instant.now());
        userRepo.save(inactive);

        var res = authUseCase.login("inactivo@test.com", "Password123");
        assertThat(res.isFailure()).isTrue();
        var err = ((Result.Failure<AuthUseCase.AuthResult>) res).error();
        assertThat(err).isInstanceOf(AppError.Unauthorized.class);
    }

    @Test
    @DisplayName("me devuelve el usuario autenticado actual")
    void meReturnsCurrentUser() {
        var reg = authUseCase.register("Agente", "agente@test.com", "Password123");
        UUID uid = ((Result.Success<AuthUseCase.AuthResult>) reg).value().user().id();

        var meRes = authUseCase.me(uid);
        assertThat(meRes.isSuccess()).isTrue();
        assertThat(((Result.Success<User>) meRes).value().id()).isEqualTo(uid);
    }
}
