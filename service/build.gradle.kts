plugins {
    id("punishments.service")
}

dependencies {
    implementation(projects.common)

    // Ktor
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
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

    // Logging
    implementation(libs.logback)

    // gRPC Server
    implementation(libs.grpc.netty.shaded)
}

application {
    mainClass.set("punishments.ApplicationKt")
}


