import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

/**
 * Convention plugin for modules that produce shadow (fat) JARs.
 */

plugins {
    id("punishments.base")
    id("com.gradleup.shadow")
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
    mergeServiceFiles()
}

