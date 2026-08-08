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
    api(kotlin("test"))
    api(libs.kotlinx.coroutines.core)
    api(libs.testcontainers.postgresql)
    implementation(libs.pty4j)
}
