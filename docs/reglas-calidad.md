# Reglas de Evaluación de Calidad de Código y Pureza Funcional

Este documento detalla las reglas de medición estáticas y dinámicas implementadas en el arnés de benchmark (`bench quality` y `bench functional`), alineadas estrictamente con la rúbrica de evaluación del curso.

---

## 1. Criterio de Calidad de Código (Peso: 15% del total)

La calidad de código se evalúa sobre el código fuente de producción (`src/main`) de cada backend utilizando análisis estático (`lizard`), verificación de configuración de compilación estricta y lectura automatizada de reportes de ejecución de pruebas (JUnit XML) y cobertura (JaCoCo XML y scoverage XML).

| Subcriterio | Peso Relativo | Métrica / Instrumento | Fórmula de Cálculo |
|:---|:---:|:---|:---|
| **Q1: Cobertura de Líneas** | 20% | JaCoCo / scoverage XML | $\min\left(1.0, \frac{\text{LineCoveragePct}}{80.0}\right)$ |
| **Q2: Tasa de Pruebas Aprobadas** | 15% | JUnit XML | $\left(\frac{\text{Passed}}{\text{Total}}\right) \times \min\left(1.0, \frac{\text{Total}}{30}\right)$ |
| **Q3: Compilación Estricta** | 15% | Banderas en build y log | 1.0 si pasa build con `-Werror` / `allWarningsAsErrors`, 0.0 de lo contrario |
| **Q4: Complejidad Ciclomática** | 15% | Complejidad media (CCN) vía `lizard` | $\text{CCN} \le 2.5 \implies 1.0$; $\text{CCN} \ge 6.0 \implies 0.0$; interpolación lineal |
| **Q5: Longitud de Funciones** | 10% | % funciones > 30 NLOC vía `lizard` | $0\% \implies 1.0$; $\ge 20\% \implies 0.0$; interpolación lineal |
| **Q6: Duplicación de Código** | 10% | Tokens / bloques duplicados (`lizard`) | $0\% \implies 1.0$; $\ge 10\% \implies 0.0$; interpolación lineal |
| **Q7: Concisión del Lenguaje** | 15% | NLOC de `src/main` vía `lizard` | $\frac{\min(\text{NLOC}_{\text{Java}}, \text{NLOC}_{\text{Scala}}, \text{NLOC}_{\text{Kotlin}})}{\text{NLOC}_{\text{Lenguaje}}}$ |

---

## 2. Criterio de Uso y Adaptación Funcional (Peso: 10% del total)

La evaluación funcional se focaliza exclusivamente en el núcleo algorítmico inmutable: `analytics/AnalyticsPipeline.*` y `analytics/ReportFormatter.*`. Estos componentes deben representar código funcional idiomático y matemáticamente puro.

| Subcriterio | Peso Relativo | Patrón Evaluado | Regla y Penalización |
|:---|:---:|:---|:---|
| **F1: Ausencia de Bucles Imperativos** | 25% | Palabras clave `for`, `while` (en Scala solo `while`) | 1.0 con 0 bucles; $-0.2$ por cada bucle encontrado (mínimo 0.0) |
| **F2: Inmutabilidad de Estado** | 25% | Variables mutables (`var`, reasignaciones) y colecciones mutables | 1.0 con 0 mutabilidades; $-0.2$ por ocurrencia (mínimo 0.0) |
| **F3: Cobertura de Operaciones de Orden Superior** | 20% | Uso de las 6 operaciones requeridas: `filter`, `map`, `groupBy`/`groupingBy`, `reduce`/`fold`/`sum`, `flatMap`, `sorted`/`sort` | $\frac{\text{Operaciones Encontradas}}{6.0}$ |
| **F4: Densidad de Lambdas** | 10% | Expresiones lambda o closures por cada 100 NLOC | $\min\left(1.0, \frac{\text{Lambdas por 100 NLOC}}{10.0}\right)$ |
| **F5: Manejo Seguro de Nulos** | 10% | Ausencia de `null` literales o forzado nulo (`!!`) | 1.0 con 0 ocurrencias; $-0.2$ por ocurrencia (mínimo 0.0) |
| **F6: Pureza de Imports** | 10% | Ausencia estricta de imports de I/O, BD o web (`java.sql`, `java.io`, `java.nio`, `java.net`, `javax.*`, `io.javalin.*`, `org.slf4j.*`, `com.zaxxer.*`, etc.) | 1.0 si 0 imports prohibidos; 0.0 si existe alguno |

---

## 3. Pesos Globales de la Rúbrica de Evaluación

| Criterio | Peso | Naturaleza |
|:---|:---:|:---|
| **Tiempo de Ejecución** | 25% | Cuantitativo (Instrumentado con arnés) |
| **Consumo de Memoria** | 20% | Cuantitativo (Muestreo continuo con Docker Stats) |
| **Rendimiento (Throughput)** | 20% | Cuantitativo (Ops/seg en pipeline y RPS en carga HTTP) |
| **Calidad de Código** | 15% | Cuantitativo / Estático (Reglas Q1 a Q7) |
| **Confiabilidad** | 10% | Cuantitativo (Tests de contrato, checksums, estabilidad) |
| **Uso Funcional** | 10% | Estático / Reglas de patrón (F1 a F6) |
| **TOTAL** | **100%** | Clasificación multicriterio automatizada |
