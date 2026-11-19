package edu.investigacion.web;

import com.fasterxml.jackson.databind.JsonNode;
import edu.investigacion.application.BenchmarkUseCase;
import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Result;
import io.javalin.http.Context;

public final class BenchmarkHandler {
    private final BenchmarkUseCase benchmarkUseCase;
    private final AuthMiddleware authMiddleware;

    public BenchmarkHandler(BenchmarkUseCase benchmarkUseCase, AuthMiddleware authMiddleware) {
        this.benchmarkUseCase = benchmarkUseCase;
        this.authMiddleware = authMiddleware;
    }

    public void runBenchmark(Context ctx) {
        if (!authMiddleware.requireAdmin(ctx)) return;
        User user = ctx.attribute("currentUser");

        JsonNode body = parseTree(ctx);
        if (body == null || !body.isObject()) {
            HttpErrorHandler.respondError(ctx, new AppError.ValidationError("Cuerpo JSON inválido"));
            return;
        }

        String scenario = body.hasNonNull("scenario") ? body.get("scenario").asText() : "";
        Long seed = body.hasNonNull("seed") ? body.get("seed").asLong() : null;
        boolean includeReport = "true".equalsIgnoreCase(ctx.queryParam("includeReport"));

        var res = benchmarkUseCase.runBenchmark(user, scenario, seed, includeReport);
        switch (res) {
            case Result.Success<BenchmarkUseCase.BenchmarkResult> s -> {
                ctx.status(200).json(JsonRenderer.renderBenchmarkResult(s.value()));
            }
            case Result.Failure<BenchmarkUseCase.BenchmarkResult> f -> {
                HttpErrorHandler.respondError(ctx, f.error());
            }
        }
    }

    public void overview(Context ctx) {
        if (!authMiddleware.requireAdmin(ctx)) return;
        User user = ctx.attribute("currentUser");

        var res = benchmarkUseCase.runOverview(user);
        switch (res) {
            case Result.Success<BenchmarkUseCase.OverviewResult> s -> {
                ctx.status(200).json(JsonRenderer.renderOverviewResult(s.value()));
            }
            case Result.Failure<BenchmarkUseCase.OverviewResult> f -> {
                HttpErrorHandler.respondError(ctx, f.error());
            }
        }
    }

    private JsonNode parseTree(Context ctx) {
        try {
            return JsonRenderer.mapper().readTree(ctx.body());
        } catch (Exception e) {
            return null;
        }
    }
}
