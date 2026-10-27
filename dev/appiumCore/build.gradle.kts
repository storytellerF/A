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
    api(projects.dev.testInfrastructure)
    implementation(libs.kotlinx.collections.immutable)
    api(kotlin("test"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(projects.api)
    api(projects.client.core)
    api(projects.shared)
    api(libs.ktor.client.core)
    api(libs.java.client)
    api(libs.testcontainers)
    api(libs.testcontainers.postgresql)
    api(libs.selenium.support)
    implementation(libs.selenium.chrome.driver)
    implementation(libs.selenium.edge.driver)
    implementation(libs.selenium.firefox.driver)
}

val testWasmCoverageRuntime =
    tasks.register<Exec>("testWasmCoverageRuntime") {
    group = "verification"
    description = "Verify browser coverage snapshot acknowledgement and unload ownership."
    inputs.file("src/main/resources/wasm-coverage.js")
    inputs.dir("src/test/js")
    commandLine("node", "--test", "src/test/js/wasm-coverage.test.cjs")
}

tasks.test { dependsOn(testWasmCoverageRuntime) }
