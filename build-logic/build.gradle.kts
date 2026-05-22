plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.plugin.kotlin)
    implementation(libs.plugin.kotlin.serialization)
    implementation(libs.plugin.shadow)
    implementation(libs.plugin.protobuf)
}

kotlin {
    jvmToolchain(
        rootProject.findProperty("javaVersion").toString().toIntOrNull()
            ?: throw IllegalStateException("Java version not specified")
    )
}

