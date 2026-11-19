package edu.investigacion.application;

import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Models.Page;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Repositories.UserRepository;
import edu.investigacion.domain.Result;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class UserUseCase {
    private final UserRepository userRepository;

    public UserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Result<Page<User>> list(
        User currentUser,
        int page,
        int limit,
        Optional<String> q,
        Optional<Role> role
    ) {
        if (!AccessPolicy.isAdmin(currentUser)) {
            return Result.fail(new AppError.Forbidden("Solo administradores pueden listar usuarios"));
        }

        var pageRes = Validators.validatePagination(page, limit);
        if (pageRes.isFailure()) {
            return Result.fail(((Result.Failure<Void>) pageRes).error());
        }

        List<User> users = userRepository.findAll(page, limit, q, role);
        long total = userRepository.countAll(q, role);
        int totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / limit);

        return Result.ok(new Page<>(users, total, page, limit, totalPages));
    }

    public Result<Void> deactivate(User currentUser, UUID targetId) {
        if (!AccessPolicy.isAdmin(currentUser)) {
            return Result.fail(new AppError.Forbidden("Solo administradores pueden desactivar usuarios"));
        }

        if (currentUser.id().equals(targetId)) {
            return Result.fail(new AppError.Conflict("El administrador no puede desactivarse a sí mismo"));
        }

        Optional<User> targetOpt = userRepository.findById(targetId);
        if (targetOpt.isEmpty() || targetOpt.get().state() != State.ACTIVE) {
            return Result.fail(new AppError.NotFound("Usuario no encontrado"));
        }

        userRepository.deactivate(targetId);
        return Result.ok(null);
    }
}
