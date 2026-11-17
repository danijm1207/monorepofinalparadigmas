package edu.investigacion.infrastructure

final case class AppConfig(
    port: Int,
    databaseUrl: String,
    databaseUser: String,
    databasePassword: String,
    dbPoolSize: Int,
    jwtSecret: String,
    jwtExpiresInHours: Long,
    corsOrigins: List[String],
    uploadDir: String,
    publicBaseUrl: String
)

object AppConfig:

  def fromEnv(): AppConfig =
    val env = sys.env

    val secret = env.get("JWT_SECRET").map(_.trim).filter(_.nonEmpty).getOrElse {
      throw new IllegalStateException("JWT_SECRET es obligatoria y no puede estar vacía")
    }

    val port = env.get("PORT").flatMap(_.toIntOption).getOrElse(8080)
    val dbUrl = env.getOrElse("DATABASE_URL", "jdbc:postgresql://localhost:55432/investigacion")
    val dbUser = env.getOrElse("DATABASE_USER", "postgres")
    val dbPass = env.getOrElse("DATABASE_PASSWORD", "postgres_dev_password_change_me")
    val poolSize = env.get("DB_POOL_SIZE").flatMap(_.toIntOption).getOrElse(10)
    val jwtHours = env.get("JWT_EXPIRES_IN_HOURS").flatMap(_.toLongOption).getOrElse(24L)
    val corsRaw = env.getOrElse("CORS_ORIGIN", "http://localhost:3000,http://localhost:8081,http://localhost:8082,http://localhost:8083")
    val corsOrigins = corsRaw.split(",").map(_.trim).filter(_.nonEmpty).toList
    val uploadDir = env.getOrElse("UPLOAD_DIR", "/data/uploads")
    val publicBaseUrl = env.getOrElse("PUBLIC_BASE_URL", s"http://localhost:$port")

    AppConfig(
      port = port,
      databaseUrl = dbUrl,
      databaseUser = dbUser,
      databasePassword = dbPass,
      dbPoolSize = poolSize,
      jwtSecret = secret,
      jwtExpiresInHours = jwtHours,
      corsOrigins = corsOrigins,
      uploadDir = uploadDir,
      publicBaseUrl = publicBaseUrl
    )
