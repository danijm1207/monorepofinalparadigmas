package edu.investigacion.web

import com.fasterxml.jackson.databind.JsonNode
import edu.investigacion.application.*
import edu.investigacion.domain.AppError
import edu.investigacion.domain.Result
import edu.investigacion.infrastructure.FileStorageService
import edu.investigacion.infrastructure.JsonTreeService
import io.javalin.Javalin

class AppRouter(
    private val authService: AuthService,
    private val caseService: CaseService,
    private val suspectService: SuspectService,
    private val evidenceService: EvidenceService,
    private val userService: UserService,
    private val analyticsService: AnalyticsService,
    private val uploadDir: String,
    private val publicBaseUrl: String
) {
    fun registerRoutes(app: Javalin) {
        // Health check (público)
        app.get("/api/health", { ctx ->
            ctx.status(200)
            val node = JsonTreeService.mapper.createObjectNode()
            node.put("status", "ok")
            ctx.json(node)
        }, AppRole.ANYONE)

        // ==================== AUTH ====================
        app.post("/api/auth/register", { ctx ->
            val bodyString = ctx.body()
            val body = runCatching { JsonTreeService.mapper.readTree(bodyString) }.getOrNull()
            val name = body?.get("name")?.asText()
            val email = body?.get("email")?.asText()
            val password = body?.get("password")?.asText()

            when (val res = authService.register(name, email, password)) {
                is Result.Success -> {
                    ctx.status(201)
                    ctx.json(JsonTreeService.authResponse(res.value.first, res.value.second))
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.ANYONE)

        app.post("/api/auth/login", { ctx ->
            val bodyString = ctx.body()
            val body = runCatching { JsonTreeService.mapper.readTree(bodyString) }.getOrNull()
            val email = body?.get("email")?.asText()
            val password = body?.get("password")?.asText()

            when (val res = authService.login(email, password)) {
                is Result.Success -> {
                    ctx.status(200)
                    ctx.json(JsonTreeService.authResponse(res.value.first, res.value.second))
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.ANYONE)

        app.get("/api/auth/me", { ctx ->
            val userId = AccessManager.getUserId(ctx)
            when (val res = authService.me(userId)) {
                is Result.Success -> {
                    ctx.status(200)
                    ctx.json(JsonTreeService.singleData(JsonTreeService.userPublicToJson(res.value)))
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.AUTHENTICATED)

        // ==================== CASOS ====================
        app.get("/api/cases", { ctx ->
            val page = ctx.queryParam("page")?.toIntOrNull() ?: 1
            val limit = ctx.queryParam("limit")?.toIntOrNull() ?: 10
            val q = ctx.queryParam("q")
            val status = ctx.queryParam("status")
            val from = ctx.queryParam("from")
            val to = ctx.queryParam("to")
            val ownerId = ctx.queryParam("ownerId")

            val userId = AccessManager.getUserId(ctx)
            val role = AccessManager.getUserRole(ctx)

            when (val res = caseService.list(page, limit, q, status, from, to, ownerId, userId, role)) {
                is Result.Success -> {
                    ctx.status(200)
                    ctx.json(JsonTreeService.pageResponse(res.value) { JsonTreeService.caseToJson(it) })
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.AUTHENTICATED)

        app.post("/api/cases", { ctx ->
            val bodyString = ctx.body()
            val body = runCatching { JsonTreeService.mapper.readTree(bodyString) }.getOrNull()
            val title = body?.get("title")?.asText()
            val description = body?.get("description")?.asText()
            val location = body?.get("location")?.asText()
            val incidentDate = body?.get("incidentDate")?.asText()
            val status = body?.get("status")?.asText()
            val imageUrl = body?.get("imageUrl")?.asText()

            val userId = AccessManager.getUserId(ctx)
            when (val res = caseService.create(title, description, location, incidentDate, status, imageUrl, userId)) {
                is Result.Success -> {
                    ctx.status(201)
                    ctx.json(JsonTreeService.singleData(JsonTreeService.caseToJson(res.value)))
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.AUTHENTICATED)

        app.get("/api/cases/{id}", { ctx ->
            val id = ctx.pathParam("id")
            val userId = AccessManager.getUserId(ctx)
            val role = AccessManager.getUserRole(ctx)

            when (val res = caseService.getById(id, userId, role)) {
                is Result.Success -> {
                    ctx.status(200)
                    ctx.json(JsonTreeService.singleData(JsonTreeService.caseToJson(res.value)))
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.AUTHENTICATED)

        app.put("/api/cases/{id}", { ctx ->
            val id = ctx.pathParam("id")
            val bodyString = ctx.body()
            val body = runCatching { JsonTreeService.mapper.readTree(bodyString) }.getOrNull()
            val title = body?.get("title")?.asText()
            val description = body?.get("description")?.asText()
            val location = body?.get("location")?.asText()
            val incidentDate = body?.get("incidentDate")?.asText()
            val status = body?.get("status")?.asText()
            val imageUrl = body?.get("imageUrl")?.asText()

            val userId = AccessManager.getUserId(ctx)
            val role = AccessManager.getUserRole(ctx)

            when (val res = caseService.update(id, title, description, location, incidentDate, status, imageUrl, userId, role)) {
                is Result.Success -> {
                    ctx.status(200)
                    ctx.json(JsonTreeService.singleData(JsonTreeService.caseToJson(res.value)))
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.AUTHENTICATED)

        app.delete("/api/cases/{id}", { ctx ->
            val id = ctx.pathParam("id")
            val userId = AccessManager.getUserId(ctx)
            val role = AccessManager.getUserRole(ctx)

            when (val res = caseService.delete(id, userId, role)) {
                is Result.Success -> ctx.status(204)
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.AUTHENTICATED)

        // ==================== SOSPECHOSOS ====================
        app.get("/api/suspects", { ctx ->
            val page = ctx.queryParam("page")?.toIntOrNull() ?: 1
            val limit = ctx.queryParam("limit")?.toIntOrNull() ?: 10
            val caseId = ctx.queryParam("caseId")
            val status = ctx.queryParam("status")
            val q = ctx.queryParam("q")

            val userId = AccessManager.getUserId(ctx)
            val role = AccessManager.getUserRole(ctx)

            when (val res = suspectService.list(page, limit, caseId, status, q, userId, role)) {
                is Result.Success -> {
                    ctx.status(200)
                    ctx.json(JsonTreeService.pageResponse(res.value) { JsonTreeService.suspectToJson(it) })
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.AUTHENTICATED)

        app.post("/api/suspects", { ctx ->
            val bodyString = ctx.body()
            val body = runCatching { JsonTreeService.mapper.readTree(bodyString) }.getOrNull()
            val caseId = body?.get("caseId")?.asText()
            val fullName = body?.get("fullName")?.asText()
            val alias = body?.get("alias")?.asText()
            val description = body?.get("description")?.asText()
            val suspicionLevel = if (body?.has("suspicionLevel") == true && !body.get("suspicionLevel").isNull) body.get("suspicionLevel").asInt() else null
            val status = body?.get("status")?.asText()
            val photoUrl = body?.get("photoUrl")?.asText()

            val userId = AccessManager.getUserId(ctx)
            val role = AccessManager.getUserRole(ctx)

            when (val res = suspectService.create(caseId, fullName, alias, description, suspicionLevel, status, photoUrl, userId, role)) {
                is Result.Success -> {
                    ctx.status(201)
                    ctx.json(JsonTreeService.singleData(JsonTreeService.suspectToJson(res.value)))
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.AUTHENTICATED)

        app.get("/api/suspects/{id}", { ctx ->
            val id = ctx.pathParam("id")
            val userId = AccessManager.getUserId(ctx)
            val role = AccessManager.getUserRole(ctx)

            when (val res = suspectService.getById(id, userId, role)) {
                is Result.Success -> {
                    ctx.status(200)
                    ctx.json(JsonTreeService.singleData(JsonTreeService.suspectToJson(res.value)))
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.AUTHENTICATED)

        app.put("/api/suspects/{id}", { ctx ->
            val id = ctx.pathParam("id")
            val bodyString = ctx.body()
            val body = runCatching { JsonTreeService.mapper.readTree(bodyString) }.getOrNull()
            val fullName = body?.get("fullName")?.asText()
            val alias = body?.get("alias")?.asText()
            val description = body?.get("description")?.asText()
            val suspicionLevel = if (body?.has("suspicionLevel") == true && !body.get("suspicionLevel").isNull) body.get("suspicionLevel").asInt() else null
            val status = body?.get("status")?.asText()
            val photoUrl = body?.get("photoUrl")?.asText()

            val userId = AccessManager.getUserId(ctx)
            val role = AccessManager.getUserRole(ctx)

            when (val res = suspectService.update(id, fullName, alias, description, suspicionLevel, status, photoUrl, userId, role)) {
                is Result.Success -> {
                    ctx.status(200)
                    ctx.json(JsonTreeService.singleData(JsonTreeService.suspectToJson(res.value)))
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.AUTHENTICATED)

        app.delete("/api/suspects/{id}", { ctx ->
            val id = ctx.pathParam("id")
            val userId = AccessManager.getUserId(ctx)
            val role = AccessManager.getUserRole(ctx)

            when (val res = suspectService.delete(id, userId, role)) {
                is Result.Success -> ctx.status(204)
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.AUTHENTICATED)

        // ==================== EVIDENCIAS ====================
        app.get("/api/evidences", { ctx ->
            val page = ctx.queryParam("page")?.toIntOrNull() ?: 1
            val limit = ctx.queryParam("limit")?.toIntOrNull() ?: 10
            val caseId = ctx.queryParam("caseId")
            val suspectId = ctx.queryParam("suspectId")
            val type = ctx.queryParam("type")
            val q = ctx.queryParam("q")

            val userId = AccessManager.getUserId(ctx)
            val role = AccessManager.getUserRole(ctx)

            when (val res = evidenceService.list(page, limit, caseId, suspectId, type, q, userId, role)) {
                is Result.Success -> {
                    ctx.status(200)
                    ctx.json(JsonTreeService.pageResponse(res.value) { JsonTreeService.evidenceToJson(it) })
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.AUTHENTICATED)

        app.post("/api/evidences", { ctx ->
            val bodyString = ctx.body()
            val body = runCatching { JsonTreeService.mapper.readTree(bodyString) }.getOrNull()
            val caseId = body?.get("caseId")?.asText()
            val suspectId = body?.get("suspectId")?.asText()
            val title = body?.get("title")?.asText()
            val description = body?.get("description")?.asText()
            val type = body?.get("type")?.asText()
            val collectedAt = body?.get("collectedAt")?.asText()
            val relevance = if (body?.has("relevance") == true && !body.get("relevance").isNull) body.get("relevance").asInt() else null
            val imageUrl = body?.get("imageUrl")?.asText()

            val userId = AccessManager.getUserId(ctx)
            val role = AccessManager.getUserRole(ctx)

            when (val res = evidenceService.create(caseId, suspectId, title, description, type, collectedAt, relevance, imageUrl, userId, role)) {
                is Result.Success -> {
                    ctx.status(201)
                    ctx.json(JsonTreeService.singleData(JsonTreeService.evidenceToJson(res.value)))
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.AUTHENTICATED)

        app.get("/api/evidences/{id}", { ctx ->
            val id = ctx.pathParam("id")
            val userId = AccessManager.getUserId(ctx)
            val role = AccessManager.getUserRole(ctx)

            when (val res = evidenceService.getById(id, userId, role)) {
                is Result.Success -> {
                    ctx.status(200)
                    ctx.json(JsonTreeService.singleData(JsonTreeService.evidenceToJson(res.value)))
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.AUTHENTICATED)

        app.put("/api/evidences/{id}", { ctx ->
            val id = ctx.pathParam("id")
            val bodyString = ctx.body()
            val body = runCatching { JsonTreeService.mapper.readTree(bodyString) }.getOrNull()
            val suspectId = body?.get("suspectId")?.asText()
            val title = body?.get("title")?.asText()
            val description = body?.get("description")?.asText()
            val type = body?.get("type")?.asText()
            val collectedAt = body?.get("collectedAt")?.asText()
            val relevance = if (body?.has("relevance") == true && !body.get("relevance").isNull) body.get("relevance").asInt() else null
            val imageUrl = body?.get("imageUrl")?.asText()

            val userId = AccessManager.getUserId(ctx)
            val role = AccessManager.getUserRole(ctx)

            when (val res = evidenceService.update(id, suspectId, title, description, type, collectedAt, relevance, imageUrl, userId, role)) {
                is Result.Success -> {
                    ctx.status(200)
                    ctx.json(JsonTreeService.singleData(JsonTreeService.evidenceToJson(res.value)))
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.AUTHENTICATED)

        app.delete("/api/evidences/{id}", { ctx ->
            val id = ctx.pathParam("id")
            val userId = AccessManager.getUserId(ctx)
            val role = AccessManager.getUserRole(ctx)

            when (val res = evidenceService.delete(id, userId, role)) {
                is Result.Success -> ctx.status(204)
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.AUTHENTICATED)

        // ==================== USUARIOS (ADMIN) ====================
        app.get("/api/users", { ctx ->
            val page = ctx.queryParam("page")?.toIntOrNull() ?: 1
            val limit = ctx.queryParam("limit")?.toIntOrNull() ?: 10
            val q = ctx.queryParam("q")
            val role = ctx.queryParam("role")

            when (val res = userService.list(page, limit, q, role)) {
                is Result.Success -> {
                    ctx.status(200)
                    ctx.json(JsonTreeService.pageResponse(res.value) { JsonTreeService.userPublicToJson(it) })
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.ADMIN)

        app.delete("/api/users/{id}", { ctx ->
            val id = ctx.pathParam("id")
            val adminId = AccessManager.getUserId(ctx)

            when (val res = userService.deactivate(id, adminId)) {
                is Result.Success -> ctx.status(204)
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.ADMIN)

        // ==================== UPLOADS ====================
        app.post("/api/uploads", { ctx ->
            val uploadedFile = ctx.uploadedFile("file")
            if (uploadedFile == null) {
                ErrorTranslator.translate(ctx, AppError.Validation("El campo file es obligatorio", emptyList()))
                return@post
            }

            if (uploadedFile.size() > 5 * 1024 * 1024) {
                ErrorTranslator.translate(ctx, AppError.PayloadTooLarge())
                return@post
            }

            val folder = ctx.formParam("folder") ?: "cases"
            val saved = FileStorageService.saveFile(uploadDir, folder, uploadedFile.content())
            if (saved == null) {
                ErrorTranslator.translate(ctx, AppError.Validation("Tipo de archivo no permitido o tamaño inválido", emptyList()))
                return@post
            }

            val (relativePath, _) = saved
            val fullUrl = "$publicBaseUrl/uploads/$relativePath"

            val dataNode = JsonTreeService.mapper.createObjectNode()
            dataNode.put("url", fullUrl)
            dataNode.put("path", relativePath)

            ctx.status(201)
            ctx.json(JsonTreeService.singleData(dataNode))
        }, AppRole.AUTHENTICATED)

        // ==================== ANALYTICS & BENCHMARK (ADMIN) ====================
        app.get("/api/analytics/overview", { ctx ->
            when (val res = analyticsService.getOverview()) {
                is Result.Success -> {
                    val root = JsonTreeService.mapper.createObjectNode()
                    val data = root.putObject("data")
                    data.put("processedRecords", res.value.processedRecords)

                    val timings = data.putObject("timings")
                    timings.put("loadNanos", res.value.timings["loadNanos"] ?: 0L)
                    timings.put("pipelineNanos", res.value.timings["pipelineNanos"] ?: 0L)

                    data.put("checksum", res.value.checksum)
                    val reportArray = data.putArray("report")
                    res.value.report.forEach { reportArray.add(it) }

                    ctx.status(200)
                    ctx.json(root)
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.ADMIN)

        app.post("/api/benchmark/run", { ctx ->
            val bodyString = ctx.body()
            val body = runCatching { JsonTreeService.mapper.readTree(bodyString) }.getOrNull()
            val scenario = body?.get("scenario")?.asText()
            val seed = if (body?.has("seed") == true && !body.get("seed").isNull) body.get("seed").asLong() else null
            val includeReport = ctx.queryParam("includeReport")?.toBoolean() ?: false

            when (val res = analyticsService.runBenchmark(scenario, seed, includeReport)) {
                is Result.Success -> {
                    val root = JsonTreeService.mapper.createObjectNode()
                    val data = root.putObject("data")
                    data.put("scenario", res.value.scenario)
                    data.put("seed", res.value.seed)

                    val counts = data.putObject("counts")
                    counts.put("cases", res.value.counts["cases"] ?: 0)
                    counts.put("suspects", res.value.counts["suspects"] ?: 0)
                    counts.put("evidences", res.value.counts["evidences"] ?: 0)

                    data.put("processedRecords", res.value.processedRecords)

                    val timings = data.putObject("timings")
                    timings.put("generationNanos", res.value.timings["generationNanos"] ?: 0L)
                    timings.put("pipelineNanos", res.value.timings["pipelineNanos"] ?: 0L)

                    val jvm = data.putObject("jvm")
                    jvm.put("heapUsedBytes", res.value.jvm["heapUsedBytes"] ?: 0L)
                    jvm.put("heapMaxBytes", res.value.jvm["heapMaxBytes"] ?: 0L)

                    data.put("checksum", res.value.checksum)
                    if (res.value.report != null) {
                        val reportArray = data.putArray("report")
                        res.value.report.forEach { reportArray.add(it) }
                    }

                    ctx.status(200)
                    ctx.json(root)
                }
                is Result.Failure -> ErrorTranslator.translate(ctx, res.error)
            }
        }, AppRole.ADMIN)
    }
}
