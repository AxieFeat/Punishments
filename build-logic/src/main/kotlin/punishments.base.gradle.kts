/**
 * Base Gradle convention plugin for modules.
 *
 * Repositories are configured centrally in settings.gradle.kts via dependencyResolutionManagement.
 */

plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(
        rootProject.findProperty("javaVersion").toString().toIntOrNull()
            ?: throw IllegalStateException("Java version not specified")
    )
}

tasks.test {
    useJUnitPlatform()
}


