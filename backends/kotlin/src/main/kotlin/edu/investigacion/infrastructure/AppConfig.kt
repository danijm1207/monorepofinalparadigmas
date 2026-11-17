package edu.investigacion.infrastructure

data class AppConfig(
    val port: Int,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePass: String,
    val dbPoolSize: Int,
    val jwtSecret: String,
    val jwtExpiresInHours: Long,
    val corsOrigins: List<String>,
    val uploadDir: String,
    val publicBaseUrl: String
) {
    companion object {
        fun fromEnv(): AppConfig {
            val jwtSecret = System.getenv("JWT_SECRET")
            require(!jwtSecret.isNullOrBlank()) { "Variable de entorno obligatoria faltante: JWT_SECRET" }

            return AppConfig(
                port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
                databaseUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:55432/investigacion",
                databaseUser = System.getenv("DATABASE_USER") ?: "postgres",
                databasePass = System.getenv("DATABASE_PASSWORD") ?: "postgres",
                dbPoolSize = System.getenv("DB_POOL_SIZE")?.toIntOrNull() ?: 10,
                jwtSecret = jwtSecret,
                jwtExpiresInHours = System.getenv("JWT_EXPIRES_IN_HOURS")?.toLongOrNull() ?: 24L,
                corsOrigins = System.getenv("CORS_ORIGIN")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("*"),
                uploadDir = System.getenv("UPLOAD_DIR") ?: "/data/uploads",
                publicBaseUrl = System.getenv("PUBLIC_BASE_URL")?.removeSuffix("/") ?: "http://localhost:8083"
            )
        }
    }
}
