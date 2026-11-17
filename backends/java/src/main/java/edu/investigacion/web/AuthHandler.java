package edu.investigacion.web;

import com.fasterxml.jackson.databind.JsonNode;
import edu.investigacion.application.AuthUseCase;
import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Result;
import io.javalin.http.Context;

public final class AuthHandler {
    private final AuthUseCase authUseCase;
    private final AuthMiddleware authMiddleware;

    public AuthHandler(AuthUseCase authUseCase, AuthMiddleware authMiddleware) {
        this.authUseCase = authUseCase;
        this.authMiddleware = authMiddleware;
    }

    public void register(Context ctx) {
        JsonNode body;
        try {
            body = JsonRenderer.mapper().readTree(ctx.body());
        } catch (Exception e) {
            HttpErrorHandler.respondError(ctx, new AppError.ValidationError("Cuerpo JSON inválido"));
            return;
        }

        if (body == null || !body.isObject()) {
            HttpErrorHandler.respondError(ctx, new AppError.ValidationError("Cuerpo JSON inválido"));
            return;
        }

        String name = body.hasNonNull("name") ? body.get("name").asText() : "";
        String email = body.hasNonNull("email") ? body.get("email").asText() : "";
        String password = body.hasNonNull("password") ? body.get("password").asText() : "";

        var res = authUseCase.register(name, email, password);
        switch (res) {
            case Result.Success<AuthUseCase.AuthResult> s -> {
                ctx.status(201).json(JsonRenderer.renderAuth(s.value().user(), s.value().token()));
            }
            case Result.Failure<AuthUseCase.AuthResult> f -> {
                HttpErrorHandler.respondError(ctx, f.error());
            }
        }
    }

    public void login(Context ctx) {
        JsonNode body;
        try {
            body = JsonRenderer.mapper().readTree(ctx.body());
        } catch (Exception e) {
            HttpErrorHandler.respondError(ctx, new AppError.ValidationError("Cuerpo JSON inválido"));
            return;
        }

        if (body == null || !body.isObject()) {
            HttpErrorHandler.respondError(ctx, new AppError.ValidationError("Cuerpo JSON inválido"));
            return;
        }

        String email = body.hasNonNull("email") ? body.get("email").asText() : "";
        String password = body.hasNonNull("password") ? body.get("password").asText() : "";

        var res = authUseCase.login(email, password);
        switch (res) {
            case Result.Success<AuthUseCase.AuthResult> s -> {
                ctx.status(200).json(JsonRenderer.renderAuth(s.value().user(), s.value().token()));
            }
            case Result.Failure<AuthUseCase.AuthResult> f -> {
                HttpErrorHandler.respondError(ctx, f.error());
            }
        }
    }

    public void me(Context ctx) {
        if (!authMiddleware.authenticate(ctx)) return;
        User user = ctx.attribute("currentUser");
        ctx.status(200).json(JsonRenderer.renderDataWrapper(JsonRenderer.renderUserPublic(user)));
    }
}
