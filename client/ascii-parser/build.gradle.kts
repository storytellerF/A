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
        androidUnitTest.dependencies {
            implementation(libs.robolectric)

            implementation(libs.javet.node.linux.arm64)
            implementation(libs.javet.node.linux.x86.x4)
            implementation(libs.javet.node.macos.arm64)
            implementation(libs.javet.node.macos.x86.x4)
            implementation(libs.javet.node.windows.x86.x4)
        }
        androidUnitTest {
            dependsOn(headlessTest)
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
        jvmMain {
        }
        jvmTest {
            dependsOn(headlessTest)
        }
        if (buildIosTarget) {
            iosMain.dependencies {
            }
            iosMain {
            }
        }
    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters", "-Xexpect-actual-classes")
    }
}

dependencies {
    androidTestImplementation(libs.androidx.ui.test.junit4.android)
    debugImplementation(libs.androidx.ui.test.manifest)
}

android {
    namespace = "com.storyteller_f.a.client.kotbase"
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
        unitTests.all {
            val dir = project.layout.buildDirectory.dir("native-libs/couchbase").get().asFile
            it.jvmArgs(
                "-Djava.library.path=$dir"
            )
        }
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}
