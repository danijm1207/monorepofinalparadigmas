# Informe Técnico: Comparativa Experimental de Paradigmas Funcionales en la JVM (Java vs. Scala vs. Kotlin)

**Asignatura:** Lenguajes y Paradigmas de Programación — Examen Final  
**Proyecto:** Sistema de Investigación Criminal (Backend Multilenguaje y Benchmark Automatizado)  
**Autor (Integrante D):** José Daniel Morales Villegas (dm6932010@gmail.com)  
**Equipo de Desarrollo:**
- Integrante A: Juan Fernando Castañeda Agudelo (Backend Java & Frontend)
- Integrante B: Sebastián Rendón (Backend Scala)
- Integrante C: Camilo Gutiérrez Castañeda (Backend Kotlin)
- Integrante D: José Daniel Morales Villegas (Benchmark, Datos Compartidos, Clasificación e Informe)

---

## 1. Resumen Ejecutivo

El presente trabajo aborda la implementación y evaluación experimental comparativa de tres arquitecturas de backend funcionalmente equivalentes desarrolladas en **Java 21**, **Scala 3.3 LTS** y **Kotlin 2.0**. El objetivo central consiste en medir con instrumentos objetivos el impacto del paradigma funcional sobre la JVM en tres dimensiones fundamentales:
1. Rendimiento computacional y latencia en procesamiento analítico masivo.
2. Huella de memoria y estabilidad ante cargas concurrentes.
3. Expresividad, calidad estática del código y pureza funcional idiomática.

Para garantizar la imparcialidad del estudio, las tres implementaciones comparten el mismo servidor HTTP liviano (**Javalin 6.3 sobre Jetty**), la misma versión del JDK (**Eclipse Temurin 21**), las mismas banderas de la máquina virtual (`-XX:MaxRAMPercentage=70 -XX:+UseG1GC`), el mismo motor de persistencia relacional (**PostgreSQL 16**) y exactamente el mismo contrato REST y esquema de base de datos.

La clasificación final se determina de forma 100% automatizada a través del arnés de medición desarrollado en Python (`bench`), el cual aplica las ponderaciones oficiales de la rúbrica del curso mediante normalización por ratios relativos, agregación geométrica por escenarios y medianas entre rondas de prueba con orden rotado.

---

## 2. Definición del Problema y Alcance

### 2.1 Dominio de la Aplicación
El sistema gestiona investigaciones criminales ficticias estructuradas en:
- **Casos:** Expedientes con estado (`OPEN`, `IN_PROGRESS`, `CLOSED`), ubicación, fecha de incidente y titularidad de usuario.
- **Sospechosos:** Personas de interés vinculadas a un caso con niveles de sospecha (0 a 100) y estados procesales.
- **Evidencias:** Elementos probatorios tipificados (`PHYSICAL`, `DIGITAL`, `DOCUMENT`, `TESTIMONY`, `FORENSIC`), relevancia (1 a 5) y fecha de recolección.
- **Usuarios y Seguridad:** Roles `ADMIN` y `USER`, autenticación mediante JWT (HS256) y contraseñas cifradas con bcrypt (coste 10).

### 2.2 Núcleo Funcional Analítico (`analytics/`)
El corazón de la comparación entre lenguajes radica en el paquete analítico inmutable. Dicho componente procesa volúmenes masivos de datos sintéticos generados deterministamente (desde 280 hasta 1 400 000 registros) ejecutando:
- Filtrado de evidencias activas y con relevancia mínima $\ge 2$.
- Ponderación de puntaje forense según el tipo de evidencia.
- Agrupación por caso y agregación de métricas de severidad.
- Agrupación temporal por mes (año $\times$ 100 + mes).
- Ranking de casos más críticos (Top 10 ordenado por puntaje descendente e ID ascendente).
- Join indexado por mapa entre evidencias y sospechosos para calcular el ranking de prioridad de investigación ($Score + SuspicionLevel$).
- Distribución de casos y evidencias según su estado procesal.
- Cálculo de sospecha promedio por estado del sospechoso.
- Verificación criptográfica mediante hash SHA-256 de las líneas canónicas del reporte.

---

## 3. Diseño Experimental y Condiciones de Equidad

Para eliminar variables confusoras asociadas a frameworks o infraestructura heterogénea, se implementó el protocolo de aislamiento estricto:

### 3.1 Entorno de Ejecución Controlado
- **Contención en Docker:** Cada backend se ejecuta en un contenedor independiente con límites de recursos impuestos por el kernel:
  - CPU: `2.0 vCPUs`
  - Memoria: `2048 MB` (2 GB límite duro)
  - Heap JVM: Limitado dinámicamente al 70% de la memoria del contenedor con Garbage Collector G1 (`-XX:MaxRAMPercentage=70 -XX:+UseG1GC`).
- **Aislamiento Temporal:** Solo se ejecuta un backend a la vez. Al finalizar cada prueba, el entorno se destruye completamente mediante `docker compose down -v` y se reinicia el volumen de almacenamiento para evitar acumulación de memoria o efectos de caché residual.
- **Rotación de Orden:** Para mitigar sesgos de acumulación térmica de CPU o estado del planificador del sistema operativo anfitrión, el orden de evaluación se rota en cada ronda:
  - Ronda 1: Java $\rightarrow$ Scala $\rightarrow$ Kotlin
  - Ronda 2: Scala $\rightarrow$ Kotlin $\rightarrow$ Java
  - Ronda 3: Kotlin $\rightarrow$ Java $\rightarrow$ Scala

### 3.2 Protocolo de Calentamiento (Warmup)
Para neutralizar el sesgo del compilador Just-In-Time (JIT HotSpot C1/C2), antes de registrar cualquier métrica se ejecutan llamadas de calentamiento no medidas (5 llamadas con escenario S y 2 llamadas con escenario M).

### 3.3 Muestreo Continuo de Memoria
Se implementó un hilo en segundo plano que ejecuta `docker stats --no-stream` cada 500 ms a lo largo de todas las fases, registrando el consumo de memoria en reposo (durante 10 s posteriores al arranque) y el pico máximo alcanzado durante la ejecución analítica y la prueba de carga.

---

## 4. Métricas y Reglas de Puntuación (Rúbrica de Evaluación)

La puntuación global se calcula mediante la agregación ponderada de los 6 criterios oficiales:

$$\text{Puntaje Final} = \sum_{i=1}^{6} (\text{Puntaje Criterio}_i \times \text{Peso}_i) \times 100$$

### 4.1 Fórmulas de Normalización
1. **Métricas donde menor es mejor** (Tiempo de ejecución, Memoria, Latencia):
   $$\text{Ratio} = \frac{\min_{L}(\text{Valor}_L)}{\text{Valor}_L}$$
2. **Métricas donde mayor es mejor** (RPS, Operaciones/segundo, Cobertura):
   $$\text{Ratio} = \frac{\text{Valor}_L}{\max_{L}(\text{Valor}_L)}$$
3. **Agregación de Escenarios:** Para métricas evaluadas sobre múltiples escenarios ($T, S, M, L, XL$), se calcula la media geométrica de los ratios obtenidos.
4. **Penalización por Fallo:** Si un backend falla en un escenario (OOM, timeout $>300$ s, HTTP 5xx o checksum incorrecto), el ratio asignado para dicho escenario es $0.0$, penalizando fuertemente la media geométrica.

### 4.2 Desglose de Pesos de la Rúbrica
- **Tiempo de Ejecución (25%):**
  - 60% Tiempo de pared mediano en `POST /api/benchmark/run`
  - 20% Tiempo de arranque hasta health OK
  - 20% Latencia $p95$ en carga HTTP con concurrencia 10
- **Consumo de Memoria (20%):**
  - 70% Pico máximo de memoria del contenedor en medición analítica
  - 30% Consumo de memoria en reposo
- **Rendimiento / Throughput (20%):**
  - 60% Registros procesados por segundo en el pipeline ($\frac{Registros}{PipelineSeconds}$)
  - 40% Solicitudes por segundo (RPS) en carga HTTP concurrencia 50
- **Calidad de Código (15%):**
  - Q1 (20%): Cobertura de líneas ($\min(1.0, \frac{\text{LineCov}}{80})$)
  - Q2 (15%): Tasa de pruebas aprobadas ($\frac{Passed}{Total} \times \min(1.0, \frac{Total}{30})$)
  - Q3 (15%): Compilación estricta sin advertencias (`-Werror`)
  - Q4 (15%): Complejidad ciclomática media por función ($\le 2.5 \implies 1.0; \ge 6.0 \implies 0.0$)
  - Q5 (10%): Funciones $>30$ líneas ($0\% \implies 1.0; \ge 20\% \implies 0.0$)
  - Q6 (10%): Tasa de duplicación de código ($0\% \implies 1.0; \ge 10\% \implies 0.0$)
  - Q7 (15%): Concisión ($\frac{\min(NLOC)}{NLOC_L}$)
- **Confiabilidad (10%):**
  - 40% Pruebas de contrato API aprobadas (mínimo 45 pruebas)
  - 30% Precisión de checksums frente al oráculo de referencia
  - 20% Inversa de la tasa de errores HTTP inesperados
  - 10% Estabilidad operativa (ausencia de caídas y OOM)
- **Uso Funcional (10%):**
  - F1 (25%): Ausencia de bucles imperativos (`for`, `while`)
  - F2 (25%): Inmutabilidad estricta (cero `var` y colecciones mutables)
  - F3 (20%): Cobertura de las 6 operaciones clave (`map`, `filter`, `groupBy`, `reduce/fold`, `flatMap`, `sorted`)
  - F4 (10%): Densidad de expresiones lambda ($\ge 10$ por cada 100 NLOC)
  - F5 (10%): Manejo seguro de nulos (cero `null` literales o `!!`)
  - F6 (10%): Pureza estricta de imports (ausencia de dependencias de I/O, BD y Web en `analytics`)

---

## 5. Arquitectura Interna de los Backends

Los tres proyectos siguen una arquitectura limpia hexagonal estructurada en capas desacopladas:
- `domain/`: Modelos inmutables (`records` en Java, `case classes` en Scala, `data classes` en Kotlin), errores representados como tipos de datos algebraicos sellados (`sealed interfaces`/`sealed classes`/`Either`) y puertos de repositorios.
- `application/`: Casos de uso y validadores puros acumulativos que retornan estructuras `Result`/`Either` sin lanzar excepciones de control de flujo.
- `infrastructure/`: Implementaciones de persistencia JDBC directa mediante SQL a mano sobre HikariCP, adaptadores de JWT y bcrypt.
- `web/`: Ruteo Javalin, middlewares de autenticación Bearer y serialización mediante el árbol de Jackson (`JsonNode`).
- `analytics/`: Módulo funcional puro sin dependencias de I/O, responsable del generador determinista (`Rng`), pipeline de agregación y formateador del reporte canónico.

---

## 6. Resultados Experimentales Consolidados

*(Esta sección se actualiza automáticamente a partir de la ejecución completa `python -m bench all` una vez integrados los tres backends en la rama develop).*

### 6.1 Tabla Consolidada de Posiciones
*(Ver `results/consolidated/ranking.md` y `results/consolidated/ranking.json`).*

### 6.2 Gráficos de Rendimiento y Memoria
- [Clasificación Final Multicriterio](../results/charts/final_ranking.png)
- [Desglose por Criterio](../results/charts/criteria_breakdown.png)
- [Tiempos de Ejecución por Escenario](../results/charts/execution_time.png)
- [Consumo de Memoria Pico y en Reposo](../results/charts/memory_consumption.png)
- [Throughput HTTP bajo Concurrencia](../results/charts/http_throughput.png)

---

## 7. Análisis de Sensibilidad y Desempate

El motor de clasificación evalúa automáticamente la robustez del ganador ante dos escenarios alternativos de ponderación:
1. **Ponderaciones Iguales:** Se asigna un peso homogéneo de $\frac{1}{6} \approx 16.67\%$ a cada uno de los 6 criterios.
2. **Prioridad en Rendimiento:** Se duplica el peso de Tiempo (33.3%) y Rendimiento (33.3%), reduciendo los 4 criterios restantes a 8.33% cada uno.

**Regla de Desempate:** Si la diferencia de puntaje global entre el primer y segundo lugar es menor a 0.5 puntos, el sistema resuelve el desempate evaluando en primer término la mayor confiabilidad y, en segundo término, el menor tiempo de ejecución acumulado.

---

## 8. Amenazas a la Validez

1. **Efecto de la Compilación JIT:** Las mediciones de escenarios pequeños pueden verse afectadas por la fase de calentamiento inicial; esto se controló ejecutando repeticiones medidas y calentamiento previo.
2. **Contención de Recursos en Monomáquina:** Aunque los contenedores aíslan CPU y memoria, comparten el bus de memoria y el subsistema de almacenamiento del anfitrión; por ello, la ejecución secuencial rotada fue obligatoria.
3. **Persistencia Compartida:** El motor PostgreSQL es común a las pruebas; para evitar contaminación de datos entre rondas, se realiza truncado en cascada y recarga determinista con `COPY`.
4. **Análisis Estático por Patrones:** Las reglas de pureza funcional se basan en análisis léxico y de AST; los comentarios y cadenas de texto fueron removidos previamente para evitar falsos positivos.

---

## 9. Reproducibilidad

Para reproducir integralmente las mediciones y generar los reportes y gráficos desde cero:
```bash
# 1. Levantar base de datos limpia:
docker compose up -d postgres

# 2. Ejecutar arnés completo:
cd benchmark
.venv/bin/python -m bench all
```
Los resultados crudos quedarán registrados en `results/raw/`, las métricas consolidadas en `results/consolidated/metrics.csv` y los gráficos en `results/charts/`.
