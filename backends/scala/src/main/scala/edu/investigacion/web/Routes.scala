package edu.investigacion.web

import io.javalin.Javalin
import edu.investigacion.domain.{AppError, AppException}

object Routes:

  def register(
      app: Javalin,
      authMiddleware: AuthMiddleware,
      authController: AuthController,
      userController: UserController,
      caseController: CaseController,
      suspectController: SuspectController,
      evidenceController: EvidenceController,
      uploadController: UploadController,
      analyticsController: AnalyticsController
  ): Unit =
    // 1. Health check público
    app.get("/api/health", ctx => {
      ctx.status(200)
      ctx.contentType("application/json")
      ctx.result("""{"status":"ok"}""")
    })

    // 2. Auth público
    app.post("/api/auth/register", ctx => authController.register(ctx))
    app.post("/api/auth/login", ctx => authController.login(ctx))

    // 3. Auth me (autenticado)
    app.before("/api/auth/me", authMiddleware)
    app.get("/api/auth/me", ctx => authController.me(ctx))

    // 4. Casos (autenticados)
    app.before("/api/cases", authMiddleware)
    app.before("/api/cases/*", authMiddleware)
    app.get("/api/cases", ctx => caseController.listCases(ctx))
    app.post("/api/cases", ctx => caseController.createCase(ctx))
    app.get("/api/cases/{id}", ctx => caseController.getCase(ctx))
    app.put("/api/cases/{id}", ctx => caseController.updateCase(ctx))
    app.delete("/api/cases/{id}", ctx => caseController.deleteCase(ctx))

    // 5. Sospechosos (autenticados)
    app.before("/api/suspects", authMiddleware)
    app.before("/api/suspects/*", authMiddleware)
    app.get("/api/suspects", ctx => suspectController.listSuspects(ctx))
    app.post("/api/suspects", ctx => suspectController.createSuspect(ctx))
    app.get("/api/suspects/{id}", ctx => suspectController.getSuspect(ctx))
    app.put("/api/suspects/{id}", ctx => suspectController.updateSuspect(ctx))
    app.delete("/api/suspects/{id}", ctx => suspectController.deleteSuspect(ctx))

    // 6. Evidencias (autenticadas)
    app.before("/api/evidences", authMiddleware)
    app.before("/api/evidences/*", authMiddleware)
    app.get("/api/evidences", ctx => evidenceController.listEvidences(ctx))
    app.post("/api/evidences", ctx => evidenceController.createEvidence(ctx))
    app.get("/api/evidences/{id}", ctx => evidenceController.getEvidence(ctx))
    app.put("/api/evidences/{id}", ctx => evidenceController.updateEvidence(ctx))
    app.delete("/api/evidences/{id}", ctx => evidenceController.deleteEvidence(ctx))

    // 7. Subida de archivos (autenticado)
    app.before("/api/uploads", authMiddleware)
    app.post("/api/uploads", ctx => uploadController.upload(ctx))

    // 8. Usuarios (solo ADMIN)
    app.before("/api/users", authMiddleware)
    app.before("/api/users", Middlewares.adminOnly)
    app.before("/api/users/*", authMiddleware)
    app.before("/api/users/*", Middlewares.adminOnly)
    app.get("/api/users", ctx => userController.listUsers(ctx))
    app.delete("/api/users/{id}", ctx => userController.deactivateUser(ctx))

    // 9. Analytics & Benchmark (solo ADMIN)
    app.before("/api/analytics/overview", authMiddleware)
    app.before("/api/analytics/overview", Middlewares.adminOnly)
    app.get("/api/analytics/overview", ctx => analyticsController.overview(ctx))

    app.before("/api/benchmark/run", authMiddleware)
    app.before("/api/benchmark/run", Middlewares.adminOnly)
    app.post("/api/benchmark/run", ctx => analyticsController.runBenchmark(ctx))

    // 10. Manejo de excepciones (nunca stack traces en respuestas)
    app.exception(classOf[AppException], (ex, ctx) => {
      Middlewares.respondError(ctx, ex.error)
    })

    app.exception(classOf[java.sql.SQLException], (ex, ctx) => {
      val state = Option(ex.getSQLState).getOrElse("")
      if state == "23505" || state == "23503" then
        Middlewares.respondError(ctx, AppError.ConflictError("Conflicto de integridad en base de datos"))
      else
        Middlewares.respondError(ctx, AppError.InternalError("Error en base de datos"))
    })

    app.exception(classOf[Exception], (_, ctx) => {
      Middlewares.respondError(ctx, AppError.InternalError("Error interno del servidor"))
    })

    app.error(404, ctx => {
      if ctx.result() == null || ctx.result().isEmpty then
        Middlewares.respondError(ctx, AppError.NotFoundError("Ruta no encontrada"))
    })

    app.error(413, ctx => {
      if ctx.result() == null || ctx.result().isEmpty then
        Middlewares.respondError(ctx, AppError.PayloadTooLargeError("El archivo excede el límite de 5 MB"))
    })
