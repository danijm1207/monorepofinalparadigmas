ThisBuild / scalaVersion := "3.3.3"
ThisBuild / version := "1.0.0"
ThisBuild / organization := "edu.investigacion"

lazy val root = (project in file("."))
  .settings(
    name := "investigacion-backend-scala",

    // Banderas estrictas de compilación requeridas por el punto 5 y evaluadas por Q3
    scalacOptions ++= Seq(
      "-Werror",
      "-deprecation",
      "-feature",
      "-Wunused:all"
    ),

    // Dependencias unificadas de la JVM (punto 5 de la especificación)
    libraryDependencies ++= Seq(
      "io.javalin" % "javalin" % "6.3.0",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.17.2",
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % "2.17.2",
      "com.zaxxer" % "HikariCP" % "5.1.0",
      "org.postgresql" % "postgresql" % "42.7.3",
      "com.auth0" % "java-jwt" % "4.4.0",
      "at.favre.lib" % "bcrypt" % "0.10.2",
      "org.slf4j" % "slf4j-simple" % "2.0.13",
      "org.scalatest" %% "scalatest" % "3.2.18" % Test
    ),

    // Reportes de prueba JUnit XML para el arnés de medición (Q2)
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-reports"),

    // Exclusiones de scoverage para código de integración con DB/servidor HTTP externo
    coverageExcludedPackages := "edu\\.investigacion\\.Main.*;edu\\.investigacion\\.infrastructure\\.Jdbc.*;edu\\.investigacion\\.infrastructure\\.Database.*",

    // Configuración de Fat-Jar con sbt-assembly
    Compile / mainClass := Some("edu.investigacion.Main"),
    assembly / mainClass := Some("edu.investigacion.Main"),
    assembly / assemblyJarName := "app.jar",
    assembly / assemblyOutputPath := baseDirectory.value / "target" / "app.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) =>
        val lower = xs.map(_.toLowerCase)
        if (lower.exists(s => s.endsWith(".sf") || s.endsWith(".dsa") || s.endsWith(".rsa"))) {
          MergeStrategy.discard
        } else {
          lower match {
            case "services" :: _ => MergeStrategy.filterDistinctLines
            case "manifest.mf" :: Nil | "index.list" :: Nil | "dependencies" :: Nil => MergeStrategy.discard
            case _ => MergeStrategy.first
          }
        }
      case _ => MergeStrategy.first
    }
  )
