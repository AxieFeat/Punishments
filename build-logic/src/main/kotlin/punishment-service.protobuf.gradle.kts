/**
 * Convention plugin for modules that use gRPC / Protobuf code generation.
 */

plugins {
    id("punishment-service.serialization")
    id("com.google.protobuf")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun lib(alias: String) = libs.findLibrary(alias).orElseThrow { IllegalStateException("Library $alias not found in version catalog") }

dependencies {
    "api"(lib("grpc-stub"))
    "api"(lib("grpc-protobuf"))
    "api"(lib("grpc-kotlin-stub"))
    "api"(lib("protobuf-kotlin"))
    "api"(lib("protobuf-java-util"))
    "api"(lib("kotlinx-coroutines-core"))
    "compileOnly"(lib("annotations-api"))
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.findVersion("protobuf").get()}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.findVersion("grpc").get()}"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${libs.findVersion("grpc-kotlin").get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
                create("grpckt")
            }
            task.builtins {
                create("kotlin")
            }
        }
    }
}

