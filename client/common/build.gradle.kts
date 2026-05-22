plugins {
    id("punishments.serialization")
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

    // Logging
    implementation(libs.slf4j)
}
