import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("punishment-service.service")
}

dependencies {
    implementation(projects.common)

    // Ktor
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.ktor.server.call.logging)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlinDatetime)
    implementation(libs.hikari)
    implementation(libs.postgres)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)

    // Redis
    implementation(libs.lettuce)

    // Cache
    implementation(libs.caffeine)

    // DI
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)

    // Metrics
    implementation(libs.micrometer.prometheus)

    // Logging
    implementation(libs.logback)

    // gRPC Server
    implementation(libs.grpc.netty.shaded)

    // Tests
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

application {
    mainClass.set("punishments.ApplicationKt")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("punishment-service")
}

