package edu.investigacion.application;

import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Repositories.UserRepository;
import edu.investigacion.domain.Result;
import edu.investigacion.domain.Security.PasswordHasher;
import edu.investigacion.domain.Security.TokenService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class AuthUseCase {
    public record AuthResult(User user, String token) {}

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;

    public AuthUseCase(
        UserRepository userRepository,
        PasswordHasher passwordHasher,
        TokenService tokenService
    ) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    public Result<AuthResult> register(String name, String email, String password) {
        var valRes = Validators.validateRegister(name, email, password);
        if (valRes.isFailure()) {
            return Result.fail(((Result.Failure<Void>) valRes).error());
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            return Result.fail(new AppError.Conflict("El email ya está registrado"));
        }

        String hash = passwordHasher.hash(password);
        Instant now = Instant.now();
        User newUser = new User(
            UUID.randomUUID(),
            name.trim(),
            normalizedEmail,
            hash,
            Role.USER,
            State.ACTIVE,
            now,
            now
        );

        User saved = userRepository.save(newUser);
        String token = tokenService.generateToken(saved);
        return Result.ok(new AuthResult(saved, token));
    }

    public Result<AuthResult> login(String email, String password) {
        var valRes = Validators.validateLogin(email, password);
        if (valRes.isFailure()) {
            return Result.fail(((Result.Failure<Void>) valRes).error());
        }

        String normalizedEmail = email.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            return Result.fail(new AppError.Unauthorized("Credenciales inválidas"));
        }

        User user = userOpt.get();
        if (user.state() != State.ACTIVE || !passwordHasher.verify(password, user.passwordHash())) {
            return Result.fail(new AppError.Unauthorized("Credenciales inválidas"));
        }

        String token = tokenService.generateToken(user);
        return Result.ok(new AuthResult(user, token));
    }

    public Result<User> me(UUID userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || userOpt.get().state() != State.ACTIVE) {
            return Result.fail(new AppError.Unauthorized("Usuario no autenticado"));
        }
        return Result.ok(userOpt.get());
    }
}
