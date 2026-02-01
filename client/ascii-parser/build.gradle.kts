@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

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

    android {
        namespace = "com.storyteller_f.a.client.ascii_parser"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTest { }
        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-rules.pro")
            }
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
        val headlessTest by creating {
            dependsOn(commonTest.get())
        }
        headlessTest.dependencies {
        }
        androidMain.dependencies {
            implementation(libs.javet.node.android)
        }
        commonMain.dependencies {
            compileOnly(libs.javet)
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.robolectric)

                implementation(libs.javet.node.linux.arm64)
                implementation(libs.javet.node.linux.x86.x4)
                implementation(libs.javet.node.macos.arm64)
                implementation(libs.javet.node.macos.x86.x4)
                implementation(libs.javet.node.windows.x86.x4)
            }
            dependsOn(headlessTest)
        }
        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.ui.test.junit4.android)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmMain.dependencies {
            implementation(libs.javet)
            implementation(libs.javet.node.linux.arm64)
            implementation(libs.javet.node.linux.x86.x4)
            implementation(libs.javet.node.macos.arm64)
            implementation(libs.javet.node.macos.x86.x4)
            implementation(libs.javet.node.windows.x86.x4)
        }
        jvmTest {
            dependsOn(headlessTest)
        }
        if (buildIosTarget) {
            iosMain.dependencies {
            }
        }
    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters", "-Xexpect-actual-classes")
    }
}

dependencies {
    androidRuntimeClasspath(libs.androidx.ui.test.manifest)
}
