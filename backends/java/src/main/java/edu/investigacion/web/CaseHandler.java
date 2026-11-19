package edu.investigacion.web;

import com.fasterxml.jackson.databind.JsonNode;
import edu.investigacion.application.CaseUseCase;
import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Enums.CaseStatus;
import edu.investigacion.domain.Models.Case;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Result;
import io.javalin.http.Context;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class CaseHandler {
    private static final Pattern UUID_REGEX = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private final CaseUseCase caseUseCase;
    private final AuthMiddleware authMiddleware;

    public CaseHandler(CaseUseCase caseUseCase, AuthMiddleware authMiddleware) {
        this.caseUseCase = caseUseCase;
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

        Optional<String> q = Optional.ofNullable(ctx.queryParam("q"));
        Optional<CaseStatus> status = parseCaseStatus(ctx.queryParam("status"));
        Optional<Instant> from = parseInstant(ctx.queryParam("from"));
        Optional<Instant> to = parseInstant(ctx.queryParam("to"));
        Optional<UUID> ownerId = parseUuid(ctx.queryParam("ownerId"));

        var res = caseUseCase.list(user, page, limit, q, status, from, to, ownerId);
        switch (res) {
            case Result.Success<edu.investigacion.domain.Models.Page<Case>> s -> {
                ctx.status(200).json(JsonRenderer.renderPage(s.value(), JsonRenderer::renderCase));
            }
            case Result.Failure<edu.investigacion.domain.Models.Page<Case>> f -> {
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

        var res = caseUseCase.getById(user, UUID.fromString(idStr));
        switch (res) {
            case Result.Success<Case> s -> {
                ctx.status(200).json(JsonRenderer.renderDataWrapper(JsonRenderer.renderCase(s.value())));
            }
            case Result.Failure<Case> f -> {
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

        String title = body.hasNonNull("title") ? body.get("title").asText() : "";
        String description = body.hasNonNull("description") ? body.get("description").asText() : "";
        String location = body.hasNonNull("location") ? body.get("location").asText() : "";
        String incidentDate = body.hasNonNull("incidentDate") ? body.get("incidentDate").asText() : "";
        Optional<CaseStatus> status = body.hasNonNull("status") ? parseCaseStatus(body.get("status").asText()) : Optional.empty();
        Optional<String> imageUrl = body.hasNonNull("imageUrl") ? Optional.of(body.get("imageUrl").asText()) : Optional.empty();

        var res = caseUseCase.create(user, title, description, location, incidentDate, status, imageUrl);
        switch (res) {
            case Result.Success<Case> s -> {
                ctx.status(201).json(JsonRenderer.renderDataWrapper(JsonRenderer.renderCase(s.value())));
            }
            case Result.Failure<Case> f -> {
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

        String title = body.hasNonNull("title") ? body.get("title").asText() : "";
        String description = body.hasNonNull("description") ? body.get("description").asText() : "";
        String location = body.hasNonNull("location") ? body.get("location").asText() : "";
        String incidentDate = body.hasNonNull("incidentDate") ? body.get("incidentDate").asText() : "";
        Optional<CaseStatus> status = body.hasNonNull("status") ? parseCaseStatus(body.get("status").asText()) : Optional.empty();
        Optional<String> imageUrl = body.hasNonNull("imageUrl") ? Optional.of(body.get("imageUrl").asText()) : Optional.empty();

        var res = caseUseCase.update(user, UUID.fromString(idStr), title, description, location, incidentDate, status, imageUrl);
        switch (res) {
            case Result.Success<Case> s -> {
                ctx.status(200).json(JsonRenderer.renderDataWrapper(JsonRenderer.renderCase(s.value())));
            }
            case Result.Failure<Case> f -> {
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

        var res = caseUseCase.delete(user, UUID.fromString(idStr));
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

    private Optional<CaseStatus> parseCaseStatus(String val) {
        if (val == null) return Optional.empty();
        try {
            return Optional.of(CaseStatus.valueOf(val.trim()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<Instant> parseInstant(String val) {
        if (val == null || val.isBlank()) return Optional.empty();
        try {
            return Optional.of(Instant.parse(val.trim()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
