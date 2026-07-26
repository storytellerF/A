import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "panelWebApp"
        browser()
        binaries.executable()
    }

    sourceSets {
        getByName("wasmJsMain").resources.srcDir(project(":shared").file("src/wasmJsMain/resources"))
        wasmJsMain.dependencies {
            implementation(projects.panel.composeApp)
            implementation(projects.client.composeCore)
            implementation(libs.ui)
        }
    }
}
