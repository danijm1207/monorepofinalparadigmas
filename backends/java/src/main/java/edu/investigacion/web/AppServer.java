package edu.investigacion.web;

import edu.investigacion.domain.AppError;
import edu.investigacion.infrastructure.AppConfig;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

import java.sql.SQLException;

public final class AppServer {
    private final Javalin app;

    public AppServer(
        AppConfig config,
        AuthHandler authHandler,
        CaseHandler caseHandler,
        SuspectHandler suspectHandler,
        EvidenceHandler evidenceHandler,
        UserHandler userHandler,
        BenchmarkHandler benchmarkHandler,
        UploadHandler uploadHandler
    ) {
        this.app = Javalin.create(cfg -> {
            cfg.bundledPlugins.enableCors(cors -> {
                for (String origin : config.corsOrigin().split(",")) {
                    String trimmed = origin.trim();
                    if (!trimmed.isEmpty()) {
                        cors.addRule(r -> {
                            r.allowHost(trimmed);
                            r.allowCredentials = true;
                        });
                    }
                }
            });
            cfg.http.maxRequestSize = 6L * 1024 * 1024; // 6MB to allow 5MB files + multipart overhead
        });

        // 1. Health
        app.get("/api/health", ctx -> ctx.status(HttpStatus.OK).json(
            JsonRenderer.mapper().createObjectNode().put("status", "ok")
        ));

        // 2. Auth
        app.post("/api/auth/register", authHandler::register);
        app.post("/api/auth/login", authHandler::login);
        app.get("/api/auth/me", authHandler::me);

        // 3. Cases
        app.get("/api/cases", caseHandler::list);
        app.post("/api/cases", caseHandler::create);
        app.get("/api/cases/{id}", caseHandler::getById);
        app.put("/api/cases/{id}", caseHandler::update);
        app.delete("/api/cases/{id}", caseHandler::delete);

        // 4. Suspects
        app.get("/api/suspects", suspectHandler::list);
        app.post("/api/suspects", suspectHandler::create);
        app.get("/api/suspects/{id}", suspectHandler::getById);
        app.put("/api/suspects/{id}", suspectHandler::update);
        app.delete("/api/suspects/{id}", suspectHandler::delete);

        // 5. Evidences
        app.get("/api/evidences", evidenceHandler::list);
        app.post("/api/evidences", evidenceHandler::create);
        app.get("/api/evidences/{id}", evidenceHandler::getById);
        app.put("/api/evidences/{id}", evidenceHandler::update);
        app.delete("/api/evidences/{id}", evidenceHandler::delete);

        // 6. Users (admin)
        app.get("/api/users", userHandler::list);
        app.delete("/api/users/{id}", userHandler::deactivate);

        // 7. Uploads & static serving
        app.post("/api/uploads", uploadHandler::upload);
        app.get("/uploads/{folder}/{filename}", uploadHandler::serve);

        // 8. Analytics & Benchmark (admin)
        app.get("/api/analytics/overview", benchmarkHandler::overview);
        app.post("/api/benchmark/run", benchmarkHandler::runBenchmark);

        // Global Exception Handler
        app.exception(Exception.class, (e, ctx) -> {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }

            if (root instanceof SQLException sqle) {
                String sqlState = sqle.getSQLState();
                if ("23505".equals(sqlState) || "23503".equals(sqlState)) {
                    HttpErrorHandler.respondError(ctx, new AppError.Conflict("Conflicto de integridad en la base de datos"));
                    return;
                }
            }

            HttpErrorHandler.respondError(ctx, new AppError.InternalError("Error interno del servidor"));
        });
    }

    public Javalin javalin() {
        return app;
    }

    public void start(int port) {
        app.start(port);
    }

    public void stop() {
        app.stop();
    }
}
