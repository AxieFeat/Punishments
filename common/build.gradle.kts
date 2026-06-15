plugins {
    id("punishment-service.protobuf")
    id("punishment-service.detekt")
    id("punishment-service.publishing")
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.coroutines.core)
    api(libs.hocon)

    testImplementation(kotlin("test"))
}
