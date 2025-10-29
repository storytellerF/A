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

    applyDefaultHierarchyTemplate()

    sourceSets {
        val headlessTest by creating {
            dependsOn(commonTest.get())
        }
        headlessTest.dependencies {
        }
        val generalJvmMain by creating {
            dependencies {
                implementation(libs.bcprov.jdk18on)
                implementation(libs.bcpkix.jdk18on)
            }
            dependsOn(commonMain.get())
        }
        val noSpecialJvmMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.dependencies {
            implementation(libs.cryptography.provider.jdk)
        }
        androidMain {
            dependsOn(generalJvmMain)
            dependsOn(noSpecialJvmMain)
        }
        androidUnitTest.dependencies {
            implementation(libs.robolectric)
        }
        androidUnitTest {
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
            dependsOn(generalJvmMain)
        }
        jvmTest {
            dependsOn(headlessTest)
        }
        if (buildIosTarget) {
            iosMain.dependencies {
                implementation(libs.cryptography.provider.openssl3.prebuilt)
            }
            iosMain {
                dependsOn(noSpecialJvmMain)
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
                dependsOn(noSpecialJvmMain)
            }
        }
    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xcontext-parameters")
    }
}

android {
    namespace = "com.storyteller_f.a.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        val javaVersion = JavaVersion.forClassVersion(libs.versions.jdk.get().toInt())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.logging.enabled", true)
            }
        }
    }
}
