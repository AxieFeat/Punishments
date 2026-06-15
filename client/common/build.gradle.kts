plugins {
    id("punishment-service.serialization")
    id("punishment-service.publishing")
}

dependencies {
    api(projects.common)

    // Redis
    implementation(libs.lettuce)

    // Cache
    implementation(libs.caffeine)

    // DI
    implementation(libs.koin.core)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // gRPC Client
    implementation(libs.grpc.okhttp)

    // HTTP Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Logging
    implementation(libs.slf4j)
}
