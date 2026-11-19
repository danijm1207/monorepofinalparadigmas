package edu.investigacion.web;

import edu.investigacion.application.UserUseCase;
import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Result;
import io.javalin.http.Context;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class UserHandler {
    private static final Pattern UUID_REGEX = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private final UserUseCase userUseCase;
    private final AuthMiddleware authMiddleware;

    public UserHandler(UserUseCase userUseCase, AuthMiddleware authMiddleware) {
        this.userUseCase = userUseCase;
        this.authMiddleware = authMiddleware;
    }

    public void list(Context ctx) {
        if (!authMiddleware.requireAdmin(ctx)) return;
        User user = ctx.attribute("currentUser");

        int page = parseIntParam(ctx.queryParam("page"), 1);
        int limit = parseIntParam(ctx.queryParam("limit"), 10);
        if (page < 0 || limit < 0) {
            HttpErrorHandler.respondError(ctx, new AppError.ValidationError("Parámetros de paginación inválidos"));
            return;
        }

        Optional<String> q = Optional.ofNullable(ctx.queryParam("q"));
        Optional<Role> role = parseRole(ctx.queryParam("role"));

        var res = userUseCase.list(user, page, limit, q, role);
        switch (res) {
            case Result.Success<edu.investigacion.domain.Models.Page<User>> s -> {
                ctx.status(200).json(JsonRenderer.renderPage(s.value(), JsonRenderer::renderUserPublic));
            }
            case Result.Failure<edu.investigacion.domain.Models.Page<User>> f -> {
                HttpErrorHandler.respondError(ctx, f.error());
            }
        }
    }

    public void deactivate(Context ctx) {
        if (!authMiddleware.requireAdmin(ctx)) return;
        User user = ctx.attribute("currentUser");

        String idStr = ctx.pathParam("id");
        if (!UUID_REGEX.matcher(idStr).matches()) {
            HttpErrorHandler.respondError(ctx, new AppError.ValidationError("El ID no es un UUID válido"));
            return;
        }

        var res = userUseCase.deactivate(user, UUID.fromString(idStr));
        switch (res) {
            case Result.Success<Void> s -> ctx.status(204);
            case Result.Failure<Void> f -> HttpErrorHandler.respondError(ctx, f.error());
        }
    }

    private int parseIntParam(String val, int defVal) {
        if (val == null) return defVal;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Optional<Role> parseRole(String val) {
        if (val == null) return Optional.empty();
        try {
            return Optional.of(Role.valueOf(val.trim()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
