import org.gradle.kotlin.dsl.kotlin
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
            compilerOptions {
                freeCompilerArgs.add("-Xwasm-attach-js-exception")
            }
        }
    }

    android {
        namespace = "com.storyteller_f.a.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    if (buildIosTarget) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    jvm()

    applyDefaultHierarchyTemplate()

    sourceSets {
        val headlessTest by creating {
            dependsOn(commonTest.get())
        }
        val jvmAndroidMain by creating {
            dependencies {
                implementation(libs.bcprov.jdk18on)
                implementation(libs.bcpkix.jdk18on)
            }
            dependsOn(commonMain.get())
        }
        val noJvmMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.dependencies {
            implementation(libs.cryptography.provider.jdk)
        }
        androidMain {
            dependsOn(jvmAndroidMain)
            dependsOn(noJvmMain)
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.robolectric)
            }
            dependsOn(headlessTest)
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.cryptography.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.markdown)
            implementation(libs.napier)
            implementation(libs.kotlinx.collections.immutable)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(libs.cryptography.provider.jdk)
            implementation(libs.logback)
            implementation(libs.icu4j)
        }
        jvmMain {
            dependsOn(jvmAndroidMain)
        }
        jvmTest {
            dependsOn(headlessTest)
        }
        if (buildIosTarget) {
            iosMain.dependencies {
                implementation(libs.cryptography.provider.openssl3.prebuilt)
            }
            iosMain {
                dependsOn(noJvmMain)
            }
        }
        if (buildWasmTarget) {
            wasmJsMain.dependencies {
                implementation(libs.cryptography.provider.webcrypto)
                implementation(npm("@noble/hashes", "1.7.2"))
                implementation(npm("ethereum-cryptography", "3.1.0"))
                implementation(npm("keccak", "3.0.4"))
                implementation(npm("@noble/curves", "1.0.0"))
            }
            wasmJsMain {
                dependsOn(noJvmMain)
            }
        }
    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }
}
