# Multi-stage Dockerfile para backend Kotlin
FROM gradle:8.7-jdk21 AS builder
WORKDIR /app
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon || true
COPY src ./src
RUN ./gradlew shadowJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd -m -u 1001 appuser && \
    mkdir -p /data/uploads && \
    chown -R appuser:appuser /data /app

COPY --from=builder --chown=appuser:appuser /app/build/libs/app.jar app.jar

USER appuser
EXPOSE 8080
ENV PORT=8080 \
    UPLOAD_DIR=/data/uploads

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseG1GC", "-jar", "app.jar"]
