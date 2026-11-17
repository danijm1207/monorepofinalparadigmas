# Backend Kotlin — Sistema de Investigación Criminal

Implementación idiomática en Kotlin 2.0.x sobre JVM 21 para el comparativo de paradigmas funcionales.

## Estructura de Paquetes
- `edu.investigacion.analytics`: Módulo puro funcional (sin IO ni frameworks).
- `edu.investigacion.domain`: Modelos inmutables, enums y tipos sellados de error y resultado.
- `edu.investigacion.application`: Casos de uso, validación pura y políticas de acceso.
- `edu.investigacion.infrastructure`: Repositorios JDBC puros, JWT, BCrypt, reloj y uploads.
- `edu.investigacion.web`: Servidor HTTP Javalin 6, serialización JSON pura en árbol (Jackson) y middlewares.

## Compilación y Pruebas
```bash
./gradlew test
./gradlew shadowJar
```

## Ejecución Docker
```bash
docker compose --profile kotlin up -d --build
```
