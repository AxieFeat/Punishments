@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Punishments"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS

    repositories {
        mavenCentral()
    }
}

pluginManagement {
    includeBuild("build-logic")

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include("service")
include("common")

client("common")
client("stress")

/**
 * Function to include client subprojects.
 * Maps "client-<name>" to the "client/<name>" directory.
 */
fun client(name: String) {
    val module = "client-$name"
    include(module)
    project(":$module").projectDir = file("client/$name")
}
