plugins {
    id("punishment-service.base")
    id("punishment-service.detekt")
}

allprojects {
    apply(plugin = "punishment-service.base")

    group = rootProject.findProperty("group") ?: throw IllegalStateException("Project group not specified")
    version = rootProject.findProperty("version") ?: throw IllegalStateException("Project version not specified")
}
