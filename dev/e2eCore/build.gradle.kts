/*
 * This is a private project. All rights reserved.
 */

plugins {
    `java-library`
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(projects.dev.testInfrastructure)
    api(projects.api)
    api(projects.client.core)
    api(projects.shared)
    api(libs.ktor.client.core)
    api(kotlin("test"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.collections.immutable)
    api(libs.testcontainers.postgresql)
    implementation(libs.pty4j)
}
