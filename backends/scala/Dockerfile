# ==============================================================================
# Multi-stage Dockerfile para Backend Scala 3 (Eclipse Temurin JDK 21 + sbt)
# ==============================================================================

FROM sbtscala/scala-sbt:eclipse-temurin-jammy-21.0.2_13_1.10.1_3.4.2 AS builder

WORKDIR /build

# Copiar configuración de sbt y pre-descargar dependencias
COPY project/ project/
COPY build.sbt .
RUN sbt update

# Copiar código fuente y ejecutar pruebas + empaquetado fat-jar
COPY src/ src/
RUN sbt test assembly

# ==============================================================================
# Imagen final de ejecución (Temurin JRE 21, usuario no-root)
# ==============================================================================
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Crear usuario sin privilegios para ejecución segura
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -s /bin/bash -m appuser

# Copiar el fat-jar generado en la etapa de construcción
COPY --from=builder /build/target/app.jar ./app.jar

# Crear directorio de subida de archivos y asignar permisos
RUN mkdir -p /data/uploads && \
    chown -R appuser:appgroup /data/uploads /app

USER appuser

EXPOSE 8080

ENV PORT=8080

# Flags JVM idénticos estipulados en el punto 5
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseG1GC", "-jar", "app.jar"]
