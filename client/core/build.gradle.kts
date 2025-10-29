import org.gradle.kotlin.dsl.implementation
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.serialization)
}

val buildIosTarget = project.findProperty("target.ios") == "true"
val buildWasmTarget = project.findProperty("target.wasm") == "true"
kotlin {
    if (buildWasmTarget) {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            browser()
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    if (buildIosTarget) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    jvm()

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(libs.napier)
            implementation(libs.kotlinx.collections.immutable)
            implementation(projects.shared)
            implementation(libs.bundles.ktor.client)
            implementation(libs.kotlinx.datetime)
            implementation(projects.api)
            implementation(libs.route4k.common)
            implementation(libs.route4k.ktor.client)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        if (buildIosTarget) {
            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

android {
    namespace = "com.storyteller_f.a.client.core"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        val javaVersion = JavaVersion.forClassVersion(libs.versions.jdk.get().toInt())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
