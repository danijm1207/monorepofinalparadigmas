# Etapa 1: Build con Gradle y JDK 21
FROM gradle:8.10-jdk21 AS builder
WORKDIR /app

# Copiar archivos de configuración de Gradle para aprovechar caché de capas
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY gradlew ./

# Descargar dependencias
RUN gradle dependencies --no-daemon || true

# Copiar código fuente y compilar fat-jar
COPY src ./src
RUN gradle shadowJar --no-daemon

# Etapa 2: Runtime ligero con JRE 21
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Crear usuario sin privilegios y directorio de uploads
RUN groupadd -r appgroup && useradd -r -g appgroup -u 1001 appuser && \
    mkdir -p /data/uploads/cases /data/uploads/suspects /data/uploads/evidences && \
    chown -R appuser:appgroup /data/uploads

# Copiar artefacto ejecutable
COPY --from=builder /app/build/libs/app.jar ./app.jar

# Variables de entorno por defecto
ENV PORT=8080 \
    DATABASE_URL=jdbc:postgresql://postgres:5432/investigacion \
    DATABASE_USER=postgres \
    DATABASE_PASSWORD=postgres_dev_password_change_me \
    DB_POOL_SIZE=10 \
    JWT_EXPIRES_IN_HOURS=24 \
    UPLOAD_DIR=/data/uploads \
    PUBLIC_BASE_URL=http://localhost:8081

EXPOSE 8080

USER appuser

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseG1GC", "-jar", "app.jar"]
