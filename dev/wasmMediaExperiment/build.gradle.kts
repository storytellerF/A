/*
 * This is a private project. All rights reserved.
 */

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "wasmMediaExperiment"
        browser()
        binaries.executable()
    }
    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.client.composeCore)
            implementation(libs.ui)
            implementation(libs.foundation)
            implementation(libs.material3)
        }
    }
}
