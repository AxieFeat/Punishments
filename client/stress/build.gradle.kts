plugins {
    id("punishment-service.service")
}

dependencies {
    implementation(projects.common)
    implementation(projects.clientCommon)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Logging
    implementation(libs.logback)
    implementation(libs.slf4j)

    // gRPC (for channel transport)
    implementation(libs.grpc.okhttp)

    // CLI args parsing
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("punishments.client.stress.StressTestMainKt")
}
