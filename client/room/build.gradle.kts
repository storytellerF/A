import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.serialization)
    id("com.google.devtools.ksp")
    id("androidx.room3")
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
        namespace = "com.storyteller_f.a.client.room"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
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

    sourceSets {
        commonMain.dependencies {
            implementation(libs.napier)
            implementation(projects.shared)
            implementation(libs.kotlinx.datetime)
            implementation(projects.client.modelStorage)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.paging)
        }
        // jvm 与 android 共享：BundledSQLiteDriver 及其日志包装器（这些在 wasm 上不可用，
        // 因为 sqlite-web 把 SQLiteDriver 的 open/prepare/step 变成了 suspend）。
        val jvmAndroidMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.androidx.sqlite.bundled)
            }
        }
        jvmMain.get().dependsOn(jvmAndroidMain)
        androidMain.get().dependsOn(jvmAndroidMain)
        if (buildWasmTarget) {
            val wasmJsMain by getting {
                dependencies {
                    implementation(libs.androidx.sqlite.web)
                    implementation(libs.kotlinx.browser)
                }
            }
        }
    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }
}

dependencies {
    val roomCompiler = libs.androidx.room.compiler.get()
    add("kspCommonMainMetadata", roomCompiler)
    add("kspJvm", roomCompiler)
    add("kspAndroid", roomCompiler)
    if (buildWasmTarget) {
        add("kspWasmJs", roomCompiler)
    }
    if (buildIosTarget) {
        add("kspIosX64", roomCompiler)
        add("kspIosArm64", roomCompiler)
        add("kspIosSimulatorArm64", roomCompiler)
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}
