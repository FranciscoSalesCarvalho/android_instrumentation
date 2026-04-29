plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    application
}

group = "com.francisco"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // CLI
    implementation("com.github.ajalt.clikt:clikt:4.2.1")

    // Ktor Client (para Claude API)
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-client-logging:2.3.7")

    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.francisco.fridagpt.Main")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

// Configuração para passar argumentos via gradle
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

// Configurar JAR executável com nome fixo
tasks.jar {
    archiveFileName.set("fridaforge.jar")

    manifest {
        attributes(
            "Main-Class" to "com.francisco.fridagpt.Main",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }

    // Include dependencies in JAR (Fat JAR)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Exclude signature files
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

// Task alternativa para criar JAR executável standalone
tasks.register<Jar>("fatJar") {
    archiveFileName.set("fridaforge.jar")

    manifest {
        attributes["Main-Class"] = "com.francisco.fridagpt.Main"
    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Make jar task build fat jar by default
tasks.named("build") {
    dependsOn("jar")
}