plugins {
    id("punishments.base")
}

allprojects {
    apply(plugin = "punishments.base")

    group = rootProject.findProperty("group") ?: throw IllegalStateException("Project group not specified")
    version = rootProject.findProperty("version") ?: throw IllegalStateException("Project version not specified")
}
