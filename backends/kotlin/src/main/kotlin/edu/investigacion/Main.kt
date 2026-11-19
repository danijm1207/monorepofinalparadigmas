package edu.investigacion

import edu.investigacion.application.*
import edu.investigacion.domain.AppError
import edu.investigacion.infrastructure.*
import edu.investigacion.web.AccessManager
import edu.investigacion.web.AppRouter
import edu.investigacion.web.ErrorTranslator
import io.javalin.Javalin
import io.javalin.http.staticfiles.Location
import java.io.File

fun main() {
    val config = AppConfig.fromEnv()

    // 1. Asegurar directorio de subidas
    File(config.uploadDir).mkdirs()

    // 2. Base de datos
    val dataSource = DatabaseManager.createDataSource(config)

    // 3. Repositorios
    val userRepository = JdbcUserRepository(dataSource)
    val caseRepository = JdbcCaseRepository(dataSource)
    val suspectRepository = JdbcSuspectRepository(dataSource)
    val evidenceRepository = JdbcEvidenceRepository(dataSource)
    val analyticsRepository = JdbcAnalyticsRepository(dataSource)

    // 4. Servicios
    val tokenService = TokenService(config.jwtSecret, config.jwtExpiresInHours)
    val authService = AuthService(userRepository, tokenService)
    val caseService = CaseService(caseRepository)
    val suspectService = SuspectService(suspectRepository, caseRepository)
    val evidenceService = EvidenceService(evidenceRepository, caseRepository, suspectRepository)
    val userService = UserService(userRepository)
    val analyticsService = AnalyticsService(analyticsRepository)

    // 5. AccessManager y Router
    val accessManager = AccessManager(tokenService)
    val router = AppRouter(
        authService = authService,
        caseService = caseService,
        suspectService = suspectService,
        evidenceService = evidenceService,
        userService = userService,
        analyticsService = analyticsService,
        uploadDir = config.uploadDir,
        publicBaseUrl = config.publicBaseUrl
    )

    // 6. Servidor HTTP Javalin 6
    val app = Javalin.create { javalinConfig ->
        // CORS
        javalinConfig.bundledPlugins.enableCors { cors ->
            cors.addRule { rule ->
                if (config.corsOrigins.contains("*")) {
                    rule.anyHost()
                } else {
                    config.corsOrigins.forEach { rule.allowHost(it) }
                }
            }
        }

        // Servir archivos estáticos subidos desde /uploads
        javalinConfig.staticFiles.add { staticFiles ->
            staticFiles.hostedPath = "/uploads"
            staticFiles.directory = config.uploadDir
            staticFiles.location = Location.EXTERNAL
        }

        // Límite de subida de 5 MB
        javalinConfig.http.maxRequestSize = 6 * 1024 * 1024

        // Configurar gestor de accesos / roles
        javalinConfig.router.mount {
            // Javalin 6 maneja roles a través de beforeMatched o route overview
        }
    }

    // Configurar middleware global de autorización según roles
    app.beforeMatched { ctx ->
        val routeRoles = ctx.routeRoles()
        accessManager.manage({ /* ya validado */ }, ctx, routeRoles)
    }

    // Manejo de errores 500 no capturados (nunca stack trace)
    app.exception(Exception::class.java) { e, ctx ->
        ErrorTranslator.translate(ctx, AppError.Internal(e.message ?: "Error interno del servidor"))
    }

    // Rutas de la API
    router.registerRoutes(app)

    // Iniciar servidor
    app.start(config.port)
}
