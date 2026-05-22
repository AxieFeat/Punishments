plugins {
    id("punishments.protobuf")
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.coroutines.core)
}
