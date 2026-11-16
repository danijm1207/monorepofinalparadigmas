# Sistema de Investigación Criminal — Monorepo y Benchmark Comparativo

Este repositorio contiene la implementación y evaluación experimental comparativa de tres arquitecturas de backend equivalentes desarrolladas en **Java**, **Scala** y **Kotlin**, bajo el paradigma funcional, junto con un arnés de benchmarking automatizado, suite de pruebas de contrato y sistema de clasificación multicriterio.

Proyecto correspondiente al examen final de **Lenguajes y Paradigmas de Programación**.

---

## 1. Reparto del Equipo y Responsabilidades

| Integrante | Rol | Rebanada de Trabajo | Responsabilidades y Carpetas |
|:---|:---:|:---|:---|
| **Juan Fernando Castañeda Agudelo** | A | Backend Java & Frontend | `backends/java/`, integración del frontend noir |
| **Sebastián Rendón** | B | Backend Scala | `backends/scala/` |
| **Camilo Gutiérrez Castañeda** | C | Backend Kotlin | `backends/kotlin/` |
| **José Daniel Morales Villegas** | D | Benchmark, Datos & Clasificación | `benchmark/`, `shared/`, `results/`, `docs/`, `docker-compose.yml`, `.env.example`, `README.md` |

---

## 2. Estructura del Monorepo

```
monorepofinalparadigmas/
├── backends/
│   ├── java/              # Backend Java 21 + Gradle (Shadow JAR) + Javalin
│   ├── scala/             # Backend Scala 3.3.x LTS + sbt (sbt-assembly) + Javalin
│   └── kotlin/            # Backend Kotlin 2.0.x + Gradle (Shadow JAR) + Javalin
├── benchmark/             # Arnés de medición, pruebas y clasificación en Python 3.11+
│   ├── bench/             # Paquete modular del arnés (runner, docker, carga, métricas)
│   ├── config/            # Reglas de scoring y reglas funcionales (YAML)
│   └── tests/             # Pruebas de contrato y tests unitarios del arnés
├── shared/                # Recursos compartidos y especificaciones
│   ├── db/                # Esquema DDL (schema.sql) y datos semilla (seed.sql)
│   ├── golden/            # Salidas maestras de verificación (T.txt, checksums.json)
│   └── spec/              # Especificación vinculante del benchmark funcional
├── results/               # Resultados generados por el arnés
│   ├── raw/               # Telemetría cruda y métricas por ronda/escenario
│   ├── consolidated/      # Métricas tabuladas (metrics.csv/json), ranking e informe
│   └── charts/            # Gráficos de dispersión, radar y rendimiento en PNG
├── docs/                  # Documentación técnica y reglas
│   ├── informe-tecnico.md # Informe técnico formal y análisis de resultados
│   └── reglas-calidad.md  # Especificación de reglas Q1-Q7 y F1-F6
├── docker-compose.yml     # Orquestación de Postgres y perfiles de backends
├── .env.example           # Plantilla de variables de entorno
└── README.md              # Documentación general del proyecto
```

---

## 3. Requisitos Previos

- **Docker y Docker Compose**: Docker 24+ con soporte para `compose v2`.
- **Python**: Python 3.11 o superior.
- **Java Development Kit (JDK)**: JDK 21 (Eclipse Temurin recomendado).
- **Herramientas de Build**: Gradle 8.x y sbt 1.10.x (para compilación y pruebas locales).

---

## 4. Puesta en Marcha Rápida (Entorno Local)

### 4.1 Configuración de Variables de Entorno

```bash
cp .env.example .env
```

### 4.2 Despliegue de Base de Datos y Backends con Docker Compose

Cada backend se ejecuta de forma aislada e independiente activando su perfil correspondiente:

```bash
# Levantar PostgreSQL e inicializar esquema y seed:
docker compose up -d postgres

# Levantar backend en Java (puerto 8081):
docker compose --profile java up -d --build

# Levantar backend en Scala (puerto 8082):
docker compose --profile scala up -d --build

# Levantar backend en Kotlin (puerto 8083):
docker compose --profile kotlin up -d --build

# Detener contenedores y limpiar volúmenes:
docker compose down -v
```

---

## 5. Arnés de Benchmark y Clasificación Automática

El arnés se ejecuta mediante el paquete `bench` en Python 3.11+.

### 5.1 Instalación de Dependencias de Python

```bash
cd benchmark
python -m venv .venv
# En Linux/macOS:
source .venv/bin/activate
# En Windows (PowerShell):
.venv\Scripts\Activate.ps1

pip install -r requirements.txt
```

### 5.2 Comandos Disponibles

- **Ejecución rápida de prueba (Smoke Test con Fake Backend o backend activo):**
  ```bash
  python -m bench --quick all
  ```
- **Ejecución de pruebas de contrato:**
  ```bash
  python -m bench contract --target http://localhost:8081
  ```
- **Análisis de calidad de código y pureza funcional:**
  ```bash
  python -m bench quality
  python -m bench functional
  ```
- **Ejecución completa del benchmark:**
  ```bash
  python -m bench run
  python -m bench consolidate
  python -m bench report
  ```

---

## 6. Verificación del Algoritmo Funcional

Para comprobar que la implementación cumple con el contrato funcional matemático, compara los hashes SHA-256 del endpoint `POST /api/benchmark/run` con los valores de referencia en `shared/golden/checksums.json`:

- **T:** `bf5c6284888610a729ceeebf66b32d45854018ff61f0cf1867f67fba67d2a91e`
- **S:** `a47b9117ce5d4a2ebf2b53a9d315435efcd58da1b54027647e7c83ea2c1941a9`
- **M:** `0d2258e413ea3166ae104f79c4f6ec51ef6bd1595a8c0ed27bcdc97aa4ff58c4`
- **L:** `888c0e28d9b7f93fbc48994aa3ac4f466761c16794b34d00e9edc1b02ac5c6b5`
- **XL:** `fda8d3a8a73432d78b1cd451ab10fa36cf13902e8f235d1f5f851590f32ad475`
