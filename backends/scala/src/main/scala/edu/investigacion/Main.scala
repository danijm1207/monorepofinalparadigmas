package edu.investigacion

import io.javalin.Javalin
import io.javalin.http.staticfiles.Location
import edu.investigacion.application.*
import edu.investigacion.infrastructure.*
import edu.investigacion.web.*

object Main:

  def main(args: Array[String]): Unit =
    // 1. Cargar configuración desde variables de entorno (falla si falta JWT_SECRET)
    val config = AppConfig.fromEnv()

    // 2. Inicializar pool de conexiones HikariCP (10 conexiones fijas)
    val dataSource = Database.createDataSource(config)

    // 3. Cableado manual de infraestructura (sin frameworks de inyección)
    val userRepository = new JdbcUserRepository(dataSource)
    val caseRepository = new JdbcCaseRepository(dataSource)
    val suspectRepository = new JdbcSuspectRepository(dataSource)
    val evidenceRepository = new JdbcEvidenceRepository(dataSource)
    val analyticsRepository = new JdbcAnalyticsRepository(dataSource)

    val hasher = new BcryptPasswordHasher(cost = 10)
    val tokenService = new JwtTokenService(config.jwtSecret, config.jwtExpiresInHours)

    // 4. Cableado de servicios de aplicación
    val authService = new AuthService(userRepository, hasher, tokenService)
    val userService = new UserService(userRepository)
    val caseService = new CaseService(caseRepository)
    val suspectService = new SuspectService(suspectRepository, caseRepository)
    val evidenceService = new EvidenceService(evidenceRepository, caseRepository, suspectRepository)
    val uploadService = new UploadService(config)
    val benchmarkService = new BenchmarkService(analyticsRepository)

    // 5. Cableado de controladores web y middlewares
    val authMiddleware = new AuthMiddleware(tokenService, userRepository)
    val authController = new AuthController(authService)
    val userController = new UserController(userService)
    val caseController = new CaseController(caseService)
    val suspectController = new SuspectController(suspectService)
    val evidenceController = new EvidenceController(evidenceService)
    val uploadController = new UploadController(uploadService)
    val analyticsController = new AnalyticsController(benchmarkService)

    // 6. Configuración e inicio del servidor Javalin
    val app = Javalin.create { javalinConfig =>
      javalinConfig.bundledPlugins.enableCors { cors =>
        cors.addRule { rule =>
          if config.corsOrigins.contains("*") then rule.anyHost()
          else
            rule.allowHost(config.corsOrigins.head, config.corsOrigins.tail*)
        }
      }

      javalinConfig.staticFiles.add { staticFiles =>
        staticFiles.hostedPath = "/uploads"
        staticFiles.directory = config.uploadDir
        staticFiles.location = Location.EXTERNAL
      }

      javalinConfig.http.maxRequestSize = 10L * 1024 * 1024
    }

    // 7. Registro de rutas
    Routes.register(
      app = app,
      authMiddleware = authMiddleware,
      authController = authController,
      userController = userController,
      caseController = caseController,
      suspectController = suspectController,
      evidenceController = evidenceController,
      uploadController = uploadController,
      analyticsController = analyticsController
    )

    // 8. Hook de cierre limpio
    sys.addShutdownHook {
      app.stop()
      dataSource.close()
    }

    // Iniciar servidor en el puerto configurado
    app.start(config.port)
