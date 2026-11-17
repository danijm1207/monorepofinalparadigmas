package edu.investigacion.infrastructure;

public record AppConfig(
    int port,
    String dbUrl,
    String dbUser,
    String dbPassword,
    int dbPoolSize,
    String jwtSecret,
    int jwtExpiresInHours,
    String corsOrigin,
    String uploadDir,
    String publicBaseUrl
) {
    public static AppConfig loadFromEnv() {
        String jwtSecret = System.getenv("JWT_SECRET");
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException("JWT_SECRET es obligatoria y no puede estar vacía");
        }

        int port = parsePositiveInt(System.getenv("PORT"), 8080);
        String dbUrl = getEnvOrDefault("DATABASE_URL", "jdbc:postgresql://localhost:55432/investigacion");
        String dbUser = getEnvOrDefault("DATABASE_USER", "postgres");
        String dbPassword = getEnvOrDefault("DATABASE_PASSWORD", "postgres_dev_password_change_me");
        int dbPoolSize = parsePositiveInt(System.getenv("DB_POOL_SIZE"), 10);
        int jwtExpires = parsePositiveInt(System.getenv("JWT_EXPIRES_IN_HOURS"), 24);
        String corsOrigin = getEnvOrDefault("CORS_ORIGIN", "http://localhost:3000,http://localhost:8081,http://localhost:8082,http://localhost:8083");
        String uploadDir = getEnvOrDefault("UPLOAD_DIR", "/data/uploads");
        String publicBaseUrl = getEnvOrDefault("PUBLIC_BASE_URL", "http://localhost:8081");

        return new AppConfig(
            port,
            dbUrl,
            dbUser,
            dbPassword,
            dbPoolSize,
            jwtSecret.trim(),
            jwtExpires,
            corsOrigin,
            uploadDir,
            publicBaseUrl
        );
    }

    private static String getEnvOrDefault(String key, String fallback) {
        String val = System.getenv(key);
        return (val != null && !val.trim().isEmpty()) ? val.trim() : fallback;
    }

    private static int parsePositiveInt(String val, int fallback) {
        if (val == null || val.trim().isEmpty()) return fallback;
        try {
            int parsed = Integer.parseInt(val.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
