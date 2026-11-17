# Backend Scala 3 — Sistema de Investigación Policial

**Integrante B:** Sebastián Rendón  
**Asignatura:** Lenguajes y Paradigmas de Programación (6to Semestre)  
**Proyecto:** Monorrepo Comparativo Multiparadigma de Backends JVM

---

## 1. Visión General de la Arquitectura

El backend en **Scala 3.3.x** está diseñado bajo los principios del paradigma funcional moderno, combinando tipado estricto, modelado algebraico de datos (ADTs), inmutabilidad por defecto y separación limpia entre la lógica de dominio pura y los efectos colaterales de infraestructura.

### Componentes y Tecnologías Utilizadas

- **Lenguaje:** Scala 3.3.3 con opciones estrictas de compilador (`-Werror`, `-deprecation`, `-feature`, `-Wunused:all`).
- **Servidor Web:** Javalin 6.3.0 (encapsulando Jetty embebido).
- **Serialización JSON:** Jackson 2.17.2 utilizando exclusivamente el API de árboles inmutables (`JsonNode`, `ObjectNode`, `ArrayNode`), sin data-binding reflexivo automático (`mapper.readValue(..., classOf[T])` está estrictamente prohibido).
- **Acceso a Datos:** JDBC nativo sobre PostgreSQL 16 con pool de conexiones HikariCP 5.1.0 (tamaño fijado en 10 conexiones).
- **Seguridad y Criptografía:**
  - Hashing de contraseñas: BCrypt con factor de costo 10 (`at.favre.lib:bcrypt:0.10.2`).
  - Tokens de sesión: JWT HMAC256 (`com.auth0:java-jwt:4.4.0`) con expiración configurable (por defecto 24 horas).
- **Contenedorización:** Dockerfile multi-etapa optimizado sobre Eclipse Temurin 21 JRE Jammy, ejecutando como usuario no privilegiado (`appuser:1001`).

---

## 2. Decisiones de Diseño Funcional

### 2.1. Modelado de Dominio con ADTs e Inmutabilidad
Todas las entidades del dominio (`User`, `Case`, `Suspect`, `Evidence`) y del pipeline analítico (`CaseRec`, `SuspectRec`, `EvidenceRec`, `SuspectStats`, `CaseReport`) se implementan como `final case class` inmutables. Los estados y categorías discretas se modelan como `enum` nativos de Scala 3 (`Role`, `EntityState`, `CaseStatus`, `SuspectStatus`, `EvidenceType`).

### 2.2. Manejo Explícito de Errores con `Either`
La lógica de negocio y validación no propaga excepciones no controladas ni retorna valores nulos. Todas las operaciones devuelven `Either[AppError, T]`, donde `AppError` es un `sealed trait` jerárquico que encapsula:
- `ValidationError` (HTTP 400) con lista exhaustiva de `FieldError`.
- `UnauthorizedError` (HTTP 401).
- `ForbiddenError` (HTTP 403).
- `NotFoundError` (HTTP 404).
- `ConflictError` (HTTP 409).
- `PayloadTooLargeError` (HTTP 413).
- `InternalError` (HTTP 500).

Para el flujo de interrupción seguro en Javalin, los middlewares lanzan controladamente `AppException(error)` que es capturado de manera determinista por el manejador global de excepciones, garantizando respuestas con la estructura uniforme requerida:
```json
{
  "error": {
    "code": "CODIGO_ERROR",
    "message": "Mensaje descriptivo"
  }
}
```

### 2.3. Pipeline Analítico 100% Puro (`analytics`)
El paquete `edu.investigacion.analytics` implementa el pipeline de agregación forense de manera 100% referencialmente transparente y libre de efectos colaterales:
- **Cero bucles iterativos:** No se utiliza `while`, `for`, `do-while` ni `foreach`.
- **Cero variables reasignables:** Todo el código utiliza únicamente `val`.
- **Cero colecciones mutables:** Se emplean exclusivamente `Vector`, `List` y `Map` inmutables de la biblioteca estándar de Scala.
- **Cero valores nulos:** Se utiliza `Option[T]` para valores opcionales.
- **Cero importaciones no estándar:** Únicamente se importa `java.time.LocalDate` para cálculos de año sobre fechas canónicas.
- **Operaciones de orden superior:** Toda la agregación se realiza mediante transformaciones funcionales compuestas (`filter`, `map`, `groupBy`, `foldLeft`, `sum`, `flatMap`, `sortWith`, `sortBy`).

El formateador canónico (`ReportFormatter`) implementa el ordenamiento lexicográfico estricto y el cálculo de SHA-256 en bloques hexadecimales en minúscula, garantizando identidad de checksum con las referencias doradas (`shared/golden/checksums.json`).

---

## 3. Estructura del Código Fuente

```
backends/scala/
├── build.sbt
├── Dockerfile
├── project/
│   ├── build.properties
│   └── plugins.sbt
├── src/
│   ├── main/
│   │   └── scala/edu/investigacion/
│   │       ├── Main.scala
│   │       ├── analytics/
│   │       │   ├── AnalyticsPipeline.scala
│   │       │   ├── Models.scala
│   │       │   ├── ReportFormatter.scala
│   │       │   ├── Rng.scala
│   │       │   └── ScenarioGenerator.scala
│   │       ├── application/
│   │       │   ├── AccessPolicy.scala
│   │       │   ├── Services.scala
│   │       │   └── Validators.scala
│   │       ├── domain/
│   │       │   ├── Errors.scala
│   │       │   ├── Models.scala
│   │       │   └── Repositories.scala
│   │       ├── infrastructure/
│   │       │   ├── Config.scala
│   │       │   ├── Database.scala
│   │       │   ├── JdbcRepositories.scala
│   │       │   └── SecurityServices.scala
│   │       └── web/
│   │           ├── Controllers.scala
│   │           ├── JsonSupport.scala
│   │           ├── Middlewares.scala
│   │           └── Routes.scala
│   └── test/
│       └── scala/edu/investigacion/
│           ├── analytics/
│           │   ├── AnalyticsPipelineSpec.scala
│           │   ├── ReportFormatterSpec.scala
│           │   ├── RngSpec.scala
│           │   └── ScenarioGeneratorSpec.scala
│           ├── application/
│           │   ├── AccessPolicySpec.scala
│           │   ├── AuthServiceSpec.scala
│           │   ├── BenchmarkServiceSpec.scala
│           │   ├── CaseServiceSpec.scala
│           │   ├── EvidenceServiceSpec.scala
│           │   ├── SuspectServiceSpec.scala
│           │   ├── UploadServiceSpec.scala
│           │   ├── UserServiceSpec.scala
│           │   └── ValidatorsSpec.scala
│           ├── domain/
│           │   └── ErrorsSpec.scala
│           ├── doubles/
│           │   └── InMemoryRepositories.scala
│           ├── infrastructure/
│           │   ├── ConfigSpec.scala
│           │   └── SecurityServicesSpec.scala
│           └── web/
│               └── JsonSupportSpec.scala
```

---

## 4. Instrucciones de Compilación y Ejecución

### 4.1. Variables de Entorno Requeridas

| Variable | Descripción | Valor por Defecto |
| :--- | :--- | :--- |
| `PORT` | Puerto HTTP del servidor | `8080` |
| `DATABASE_URL` | URL JDBC de conexión a PostgreSQL | `jdbc:postgresql://postgres:5432/investigacion` |
| `DATABASE_USER` | Usuario de base de datos | `postgres` |
| `DATABASE_PASSWORD` | Contraseña de base de datos | `postgres_dev_password_change_me` |
| `DB_POOL_SIZE` | Tamaño del pool de HikariCP | `10` |
| `JWT_SECRET` | Llave secreta HMAC256 (mínimo 256 bits) | *Requerida* |
| `JWT_EXPIRES_IN_HOURS`| Tiempo de expiración del token JWT | `24` |
| `CORS_ORIGIN` | Orígenes permitidos (separados por coma) | `http://localhost:3000,...` |
| `UPLOAD_DIR` | Directorio local de almacenamiento de archivos | `/data/uploads` |
| `PUBLIC_BASE_URL` | URL base pública para construir enlaces a archivos | `http://localhost:8082` |

### 4.2. Ejecución con Docker Compose

Para levantar PostgreSQL y el backend de Scala bajo el perfil `scala`:

```bash
docker-compose --profile scala up -d --build
```

El servicio estará disponible en `http://localhost:8082`.

Para detener los contenedores:
```bash
docker-compose --profile scala down
```

### 4.3. Compilación y Pruebas Unitarias con sbt

Para compilar estrictamente y ejecutar la suite completa de 70 pruebas unitarias con reporte JUnit XML:
```bash
sbt test
```

Para generar el reporte de cobertura de código con `sbt-scoverage`:
```bash
sbt "clean; coverage; test; coverageReport"
```
Los reportes se generan en `target/test-reports/` (JUnit XML) y `target/scala-3.3.3/scoverage-report/scoverage.xml`.

---

## 5. Resultados de Verificación y Métricas

### 5.1. Pruebas de Contrato HTTP (`test_contract.py`)
Ejecutadas contra el contenedor en vivo en el puerto 8082:
- **Total de pruebas:** 45
- **Aprobadas:** 45 (100%)
- **Fallidas:** 0
- **Tiempo de ejecución:** 16.80s
- **Aspectos validados:** Health check, registro y login con BCrypt (costo 10), protección de contraseña en respuestas, control de acceso RBAC (ADMIN vs USER), aislamiento multi-tenant por propietario, validación estricta de límites de paginación (400 ante parámetros inválidos), operaciones CRUD sobre Casos, Sospechosos y Evidencias, subida multipart y límite de 5 MB, benchmarks funcionales y verificación de checksums canónicos.

### 5.2. Evaluación de Pureza Funcional (`bench functional`)
- **Puntuación F-Score:** **100.0 / 100.0 pts**
- **F1 (Inmutabilidad):** 1.0 (0 variables reasignables `var`, 0 colecciones mutables)
- **F2 (Ausencia de bucles):** 1.0 (0 bucles `while`, `for`, `do-while`, `foreach`)
- **F3 (Operaciones de orden superior):** 1.0 (6/6 operaciones encontradas: `filter`, `map`, `groupBy`, `foldLeft`/`reduce`, `sum`, `flatMap`/`sortWith`)
- **F4 (Ausencia de nulos):** 1.0 (0 usos de `null`, uso consistente de `Option`)
- **F5 (Biblioteca estándar):** 1.0 (0 importaciones de utilidades externas prohibidas)
- **F6 (Checksums dorados exactos):** 1.0 (Coincidencia bit a bit con referencias en SHA-256 para escenarios T, S, M, L, XL con semilla 42).

### 5.3. Calidad del Código y Pruebas (`bench quality`)
- **Puntuación Q-Score:** **87.6 / 100.0 pts**
- **Q1 (Cobertura):** 30.4% de sentencias cubiertas en pruebas unitarias puras sin base de datos.
- **Q2 (Tasa de pruebas):** 1.0 (70 pruebas unitarias aprobadas de 70 ejecutadas, superando el mínimo requerido de 30).
- **Q3 (Compilación estricta):** 1.0 (Banderas `-Werror`, `-deprecation`, `-feature`, `-Wunused:all` activas en `build.sbt`).
- **Q4 (Complejidad ciclomática CCN):** 1.0 (CCN promedio de 1.15, muy inferior al umbral óptimo de 2.5).
- **Q5 (Longitud de funciones):** 1.0 (0% de funciones con más de 30 líneas).
- **Q6 (Duplicación de código):** 1.0 (0.0% de duplicación detectada).
- **Q7 (Concisión):** 1.0 (Código conciso y expresivo en Scala 3).
