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
    }

    sourceSets {
        getByName("wasmJsMain").resources.srcDir(project(":shared").file("src/wasmJsMain/resources"))
        wasmJsMain.dependencies {
            implementation(projects.app.composeApp)
            implementation(projects.client.composeCore)
            implementation(libs.ui)
        }
    }
}
