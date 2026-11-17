package edu.investigacion.web;

import com.fasterxml.jackson.databind.JsonNode;
import edu.investigacion.application.EvidenceUseCase;
import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Enums.EvidenceType;
import edu.investigacion.domain.Models.Evidence;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Result;
import io.javalin.http.Context;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class EvidenceHandler {
    private static final Pattern UUID_REGEX = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private final EvidenceUseCase evidenceUseCase;
    private final AuthMiddleware authMiddleware;

    public EvidenceHandler(EvidenceUseCase evidenceUseCase, AuthMiddleware authMiddleware) {
        this.evidenceUseCase = evidenceUseCase;
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
        Optional<UUID> suspectId = parseUuid(ctx.queryParam("suspectId"));
        Optional<EvidenceType> type = parseEvidenceType(ctx.queryParam("type"));
        Optional<String> q = Optional.ofNullable(ctx.queryParam("q"));

        var res = evidenceUseCase.list(user, page, limit, caseId, suspectId, type, q);
        switch (res) {
            case Result.Success<edu.investigacion.domain.Models.Page<Evidence>> s -> {
                ctx.status(200).json(JsonRenderer.renderPage(s.value(), JsonRenderer::renderEvidence));
            }
            case Result.Failure<edu.investigacion.domain.Models.Page<Evidence>> f -> {
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

        var res = evidenceUseCase.getById(user, UUID.fromString(idStr));
        switch (res) {
            case Result.Success<Evidence> s -> {
                ctx.status(200).json(JsonRenderer.renderDataWrapper(JsonRenderer.renderEvidence(s.value())));
            }
            case Result.Failure<Evidence> f -> {
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

        Optional<UUID> suspectId = body.hasNonNull("suspectId") ? parseUuid(body.get("suspectId").asText()) : Optional.empty();
        String title = body.hasNonNull("title") ? body.get("title").asText() : "";
        String description = body.hasNonNull("description") ? body.get("description").asText() : "";
        String type = body.hasNonNull("type") ? body.get("type").asText() : "";
        String collectedAt = body.hasNonNull("collectedAt") ? body.get("collectedAt").asText() : "";
        Integer relevance = body.hasNonNull("relevance") ? body.get("relevance").asInt() : null;
        Optional<String> imageUrl = body.hasNonNull("imageUrl") ? Optional.of(body.get("imageUrl").asText()) : Optional.empty();

        var res = evidenceUseCase.create(user, UUID.fromString(caseIdStr), suspectId, title, description, type, collectedAt, relevance, imageUrl);
        switch (res) {
            case Result.Success<Evidence> s -> {
                ctx.status(201).json(JsonRenderer.renderDataWrapper(JsonRenderer.renderEvidence(s.value())));
            }
            case Result.Failure<Evidence> f -> {
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

        Optional<UUID> suspectId = body.hasNonNull("suspectId") ? parseUuid(body.get("suspectId").asText()) : Optional.empty();
        String title = body.hasNonNull("title") ? body.get("title").asText() : "";
        String description = body.hasNonNull("description") ? body.get("description").asText() : "";
        String type = body.hasNonNull("type") ? body.get("type").asText() : "";
        String collectedAt = body.hasNonNull("collectedAt") ? body.get("collectedAt").asText() : "";
        Integer relevance = body.hasNonNull("relevance") ? body.get("relevance").asInt() : null;
        Optional<String> imageUrl = body.hasNonNull("imageUrl") ? Optional.of(body.get("imageUrl").asText()) : Optional.empty();

        var res = evidenceUseCase.update(user, UUID.fromString(idStr), suspectId, title, description, type, collectedAt, relevance, imageUrl);
        switch (res) {
            case Result.Success<Evidence> s -> {
                ctx.status(200).json(JsonRenderer.renderDataWrapper(JsonRenderer.renderEvidence(s.value())));
            }
            case Result.Failure<Evidence> f -> {
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

        var res = evidenceUseCase.delete(user, UUID.fromString(idStr));
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

    private Optional<EvidenceType> parseEvidenceType(String val) {
        if (val == null) return Optional.empty();
        try {
            return Optional.of(EvidenceType.valueOf(val.trim()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
