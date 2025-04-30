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
        val noWasmMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.couchbase.lite)
                implementation(libs.couchbase.lite.ktx)
            }
        }
        val cJvmMain by creating {
            dependsOn(commonMain.get())
            dependencies {

            }
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        androidMain {
            dependsOn(noWasmMain)
            dependsOn(cJvmMain)
        }
        commonMain.dependencies {
            implementation(projects.shared)
                        implementation(libs.bundles.ktor.client)
            implementation(libs.kotlinx.datetime)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain {
            dependsOn(noWasmMain)
            dependsOn(cJvmMain)
        }
        if (buildIosTarget) {
            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
            }
            iosMain {
                dependsOn(noWasmMain)
            }
        }
    }
}

android {
    namespace = "com.storyteller_f.a.client_lib"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
