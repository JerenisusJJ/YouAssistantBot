plugins {
    application
    kotlin("jvm") version "1.9.10"
    id("io.ktor.plugin") version "2.3.5"
    id("org.jlleitschuh.gradle.ktlint") version "11.5.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.1"
    jacoco
}

group = "com.aiassistant"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("com.aiassistant.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.lightbend.com/releases") }
}

dependencies {
    // Ktor
    implementation("io.ktor:ktor-server-core:2.3.5")
    implementation("io.ktor:ktor-server-netty:2.3.5")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.5")
    implementation("io.ktor:ktor-serialization-gson:2.3.5")

    // PostgreSQL
    implementation("org.postgresql:postgresql:42.6.0")

    // TelegramBots (for future)
    implementation("org.telegram:telegrambots:6.5.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // Config
    implementation("com.typesafe:config:1.4.9")

    // Testing
    testImplementation("io.ktor:ktor-server-tests:2.3.5")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.2")
    testImplementation("io.kotest:kotest-assertions-core:5.9.2")
    testImplementation("io.mockk:mockk:1.13.6")
}

jacoco {
    toolVersion = "0.8.10"
}

tasks {
    test {
        useJUnitPlatform()
        finalizedBy("jacocoTestReport")
    }

    jacocoTestReport {
        dependsOn(test)
        reports {
            xml.required.set(true)
            csv.required.set(false)
            html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
        }
    }
}
