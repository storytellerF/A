import org.gradle.kotlin.dsl.implementation
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
            browser {
                commonWebpackConfig {
                    devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                        static = (static ?: mutableListOf()).apply {
                            // Serve sources to debug inside browser
                            add(project.projectDir.path)
                        }
                    }
                }
                testTask {
                    useKarma {
                        useChrome()
                    }
                }
            }
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
        val cJvmMain by creating {
            dependencies {
                implementation(libs.bcprov.jdk18on)
                implementation(libs.bcpkix.jdk18on)
            }
            dependsOn(commonMain.get())
        }
        val noPjvmMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.dependencies {
            implementation(libs.cryptography.provider.jdk)
        }
        androidMain {
            dependsOn(cJvmMain)
            dependsOn(noPjvmMain)
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.cryptography.core)
            api(libs.kotlinx.datetime)
            implementation(libs.markdown)
            api(libs.napier)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(libs.cryptography.provider.jdk)
            implementation(libs.logback)
        }
        jvmMain {
            dependsOn(cJvmMain)
        }
        if (buildIosTarget) {
            iosMain.dependencies {
                implementation(libs.cryptography.provider.openssl3.prebuilt)
            }
            iosMain {
                dependsOn(noPjvmMain)
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
                dependsOn(noPjvmMain)
            }
        }
    }
}

android {
    namespace = "com.storyteller_f.a.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
