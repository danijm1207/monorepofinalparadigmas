plugins {
    java
    jacoco
    id("com.gradleup.shadow") version "8.3.5"
}

group = "edu.investigacion"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    // Web Framework & Server
    implementation("io.javalin:javalin:6.3.0")

    // JSON Processing (Tree model only)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")

    // Database & Connection Pooling
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.4")

    // Security & Auth
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("at.favre.lib:bcrypt:0.10.2")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.13")

    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml"))
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                include("edu/investigacion/domain/**")
                include("edu/investigacion/application/**")
                include("edu/investigacion/analytics/**")
            }
        })
    )
}

tasks.shadowJar {
    archiveFileName.set("app.jar")
    manifest {
        attributes["Main-Class"] = "edu.investigacion.Main"
    }
    mergeServiceFiles()
}
