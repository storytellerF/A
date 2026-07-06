plugins {
    `java-library`
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(kotlin("test"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(project(":api"))
    api(project(":dev:core"))
    api(project(":client:core"))
    api(project(":shared"))
    api(libs.ktor.client.core)
    api(libs.java.client)
    api(libs.testcontainers.postgresql)
}
