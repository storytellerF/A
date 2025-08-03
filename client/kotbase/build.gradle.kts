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
                            // Serve sources to debug inside the browser
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
                implementation(libs.napier)
                implementation(libs.couchbase.lite)
                implementation(libs.couchbase.lite.ktx)
            }
        }
        val noWasmTest by creating {
            dependsOn(commonTest.get())
        }
        val generalJvmMain by creating {
            dependsOn(commonMain.get())
            dependencies {

            }
        }
        androidMain.dependencies {
        }
        androidMain {
            dependsOn(noWasmMain)
            dependsOn(generalJvmMain)
        }
        androidUnitTest {
            dependsOn(noWasmTest)
        }
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(projects.client.storage)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
        }
        jvmMain {
            dependsOn(noWasmMain)
            dependsOn(generalJvmMain)
        }
        jvmTest {
            dependsOn(noWasmTest)
        }
        if (buildIosTarget) {
            iosMain.dependencies {
            }
            iosMain {
                dependsOn(noWasmMain)
            }
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

android {
    namespace = "com.storyteller_f.a.client.kotbase"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
