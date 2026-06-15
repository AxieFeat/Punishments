/**
 * Convention plugin for the service module (Ktor application with shadow JAR).
 */

plugins {
    id("punishment-service.serialization")
    id("punishment-service.shadow")
    application
}

// Fix implicit dependency between shadowJar and application plugin tasks
tasks.named("startScripts") { dependsOn(tasks.named("shadowJar")) }
tasks.named("distZip") { dependsOn(tasks.named("shadowJar")) }
tasks.named("distTar") { dependsOn(tasks.named("shadowJar")) }
tasks.named("startShadowScripts") { dependsOn(tasks.named("jar")) }

