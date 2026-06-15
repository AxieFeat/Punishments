import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

/**
 * Convention plugin for modules that should match with the code style of project.
 */

plugins {
    id("io.gitlab.arturbosch.detekt")
}

extensions.configure<DetektExtension>("detekt") {
    buildUponDefaultConfig = true

    val globalProjectConfiguration = rootProject.layout.projectDirectory.file("config/detekt.yml").asFile
    val localProjectConfiguration = project.layout.projectDirectory.file("config/detekt.yml").asFile

    if (localProjectConfiguration.exists()) {
        config.setFrom(localProjectConfiguration, globalProjectConfiguration)
    } else {
        config.setFrom(globalProjectConfiguration)
    }

    baseline = project.layout.projectDirectory.file("config/baseline.xml").asFile
}

tasks.named<Detekt>("detekt") {
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}
