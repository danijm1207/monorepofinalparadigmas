package edu.investigacion.web;

import com.fasterxml.jackson.databind.JsonNode;
import edu.investigacion.application.SuspectUseCase;
import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Enums.SuspectStatus;
import edu.investigacion.domain.Models.Suspect;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Result;
import io.javalin.http.Context;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class SuspectHandler {
    private static final Pattern UUID_REGEX = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private final SuspectUseCase suspectUseCase;
    private final AuthMiddleware authMiddleware;

    public SuspectHandler(SuspectUseCase suspectUseCase, AuthMiddleware authMiddleware) {
        this.suspectUseCase = suspectUseCase;
        this.authMiddleware = authMiddleware;
    }

    public void list(Context ctx) {
        if (!authMiddleware.authenticate(ctx)) return;
        User user = ctx.attribute("currentUser");

        int page = parseIntParam(ctx.queryParam("page"), 1);
        int limit = parseIntParam(ctx.queryParam("limit"), 10);
        if (page < 0 || limit < 0) {
            HttpErrorHandler.respondError(ctx, new AppError.ValidationError("Parámetros de paginación inválidos"));
            return;
        }

        Optional<UUID> caseId = parseUuid(ctx.queryParam("caseId"));
        Optional<SuspectStatus> status = parseSuspectStatus(ctx.queryParam("status"));
        Optional<String> q = Optional.ofNullable(ctx.queryParam("q"));

        var res = suspectUseCase.list(user, page, limit, caseId, status, q);
        switch (res) {
            case Result.Success<edu.investigacion.domain.Models.Page<Suspect>> s -> {
                ctx.status(200).json(JsonRenderer.renderPage(s.value(), JsonRenderer::renderSuspect));
            }
            case Result.Failure<edu.investigacion.domain.Models.Page<Suspect>> f -> {
                HttpErrorHandler.respondError(ctx, f.error());
            }
        }
    }

    public void getById(Context ctx) {
        if (!authMiddleware.authenticate(ctx)) return;
        User user = ctx.attribute("currentUser");

        String idStr = ctx.pathParam("id");
        if (!UUID_REGEX.matcher(idStr).matches()) {
            HttpErrorHandler.respondError(ctx, new AppError.ValidationError("El ID no es un UUID válido"));
            return;
        }

        var res = suspectUseCase.getById(user, UUID.fromString(idStr));
        switch (res) {
            case Result.Success<Suspect> s -> {
                ctx.status(200).json(JsonRenderer.renderDataWrapper(JsonRenderer.renderSuspect(s.value())));
            }
            case Result.Failure<Suspect> f -> {
                HttpErrorHandler.respondError(ctx, f.error());
            }
        }
    }

    public void create(Context ctx) {
        if (!authMiddleware.authenticate(ctx)) return;
        User user = ctx.attribute("currentUser");

        JsonNode body = parseTree(ctx);
        if (body == null || !body.isObject()) {
            HttpErrorHandler.respondError(ctx, new AppError.ValidationError("Cuerpo JSON inválido"));
            return;
        }

        String caseIdStr = body.hasNonNull("caseId") ? body.get("caseId").asText() : "";
        if (!UUID_REGEX.matcher(caseIdStr).matches()) {
            HttpErrorHandler.respondError(ctx, new AppError.ValidationError("caseId debe ser un UUID válido"));
            return;
        }

        String fullName = body.hasNonNull("fullName") ? body.get("fullName").asText() : "";
        Optional<String> alias = body.hasNonNull("alias") ? Optional.of(body.get("alias").asText()) : Optional.empty();
        Optional<String> description = body.hasNonNull("description") ? Optional.of(body.get("description").asText()) : Optional.empty();
        Integer suspicion = body.hasNonNull("suspicionLevel") ? body.get("suspicionLevel").asInt() : 0;
        Optional<SuspectStatus> status = body.hasNonNull("status") ? parseSuspectStatus(body.get("status").asText()) : Optional.empty();
        Optional<String> photoUrl = body.hasNonNull("photoUrl") ? Optional.of(body.get("photoUrl").asText()) : Optional.empty();

        var res = suspectUseCase.create(user, UUID.fromString(caseIdStr), fullName, alias, description, suspicion, status, photoUrl);
        switch (res) {
            case Result.Success<Suspect> s -> {
                ctx.status(201).json(JsonRenderer.renderDataWrapper(JsonRenderer.renderSuspect(s.value())));
            }
            case Result.Failure<Suspect> f -> {
                HttpErrorHandler.respondError(ctx, f.error());
            }
        }
    }

    public void update(Context ctx) {
        if (!authMiddleware.authenticate(ctx)) return;
        User user = ctx.attribute("currentUser");

        String idStr = ctx.pathParam("id");
        if (!UUID_REGEX.matcher(idStr).matches()) {
            HttpErrorHandler.respondError(ctx, new AppError.ValidationError("El ID no es un UUID válido"));
            return;
        }

        JsonNode body = parseTree(ctx);
        if (body == null || !body.isObject()) {
            HttpErrorHandler.respondError(ctx, new AppError.ValidationError("Cuerpo JSON inválido"));
            return;
        }

        String fullName = body.hasNonNull("fullName") ? body.get("fullName").asText() : "";
        Optional<String> alias = body.hasNonNull("alias") ? Optional.of(body.get("alias").asText()) : Optional.empty();
        Optional<String> description = body.hasNonNull("description") ? Optional.of(body.get("description").asText()) : Optional.empty();
        Integer suspicion = body.hasNonNull("suspicionLevel") ? body.get("suspicionLevel").asInt() : null;
        Optional<SuspectStatus> status = body.hasNonNull("status") ? parseSuspectStatus(body.get("status").asText()) : Optional.empty();
        Optional<String> photoUrl = body.hasNonNull("photoUrl") ? Optional.of(body.get("photoUrl").asText()) : Optional.empty();

        var res = suspectUseCase.update(user, UUID.fromString(idStr), fullName, alias, description, suspicion, status, photoUrl);
        switch (res) {
            case Result.Success<Suspect> s -> {
                ctx.status(200).json(JsonRenderer.renderDataWrapper(JsonRenderer.renderSuspect(s.value())));
            }
            case Result.Failure<Suspect> f -> {
                HttpErrorHandler.respondError(ctx, f.error());
            }
        }
    }

    public void delete(Context ctx) {
        if (!authMiddleware.authenticate(ctx)) return;
        User user = ctx.attribute("currentUser");

        String idStr = ctx.pathParam("id");
        if (!UUID_REGEX.matcher(idStr).matches()) {
            HttpErrorHandler.respondError(ctx, new AppError.ValidationError("El ID no es un UUID válido"));
            return;
        }

        var res = suspectUseCase.delete(user, UUID.fromString(idStr));
        switch (res) {
            case Result.Success<Void> s -> ctx.status(204);
            case Result.Failure<Void> f -> HttpErrorHandler.respondError(ctx, f.error());
        }
    }

    private JsonNode parseTree(Context ctx) {
        try {
            return JsonRenderer.mapper().readTree(ctx.body());
        } catch (Exception e) {
            return null;
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

    private Optional<UUID> parseUuid(String val) {
        if (val == null || !UUID_REGEX.matcher(val).matches()) return Optional.empty();
        return Optional.of(UUID.fromString(val));
    }

    private Optional<SuspectStatus> parseSuspectStatus(String val) {
        if (val == null) return Optional.empty();
        try {
            return Optional.of(SuspectStatus.valueOf(val.trim()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
