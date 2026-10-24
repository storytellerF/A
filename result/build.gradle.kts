/*
 * This is a private project. All rights reserved.
 */

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("wasm-browser-test-interop")
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(21)
    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
    if (project.findProperty("target.ios") == "true") {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }
    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
}
