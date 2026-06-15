import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy

/**
 * Convention plugin for modules that produce shadow (fat) JARs.
 */

plugins {
    id("punishment-service.base")
    id("com.gradleup.shadow")
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
    // Shadow 9 excludes duplicates before transformers see them, so
    // service descriptors like Flyway's must opt back into merging.
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    mergeServiceFiles()
}

