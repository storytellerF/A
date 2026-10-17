/*
 * This is a private project. All rights reserved.
 */

plugins {
    id("test-artifact-reports")
    `java-library`
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.testcontainers.postgresql)
    testImplementation(kotlin("test"))
}
