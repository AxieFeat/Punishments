plugins {
    id("punishments.protobuf")
    id("punishments.detekt")
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.coroutines.core)
    api(libs.hocon)

    testImplementation(kotlin("test"))
}
