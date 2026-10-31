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
        outputModuleName = "appWebApp"
        browser()
        binaries.executable()
        compilerOptions.freeCompilerArgs.add("-Xpartial-linkage-loglevel=error")
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.app.composeApp)
            implementation(projects.client.composeCore)
            implementation(projects.client.core)
            implementation(projects.shared)
            implementation(libs.ui)
            implementation(libs.components.resources)
        }
    }
}
