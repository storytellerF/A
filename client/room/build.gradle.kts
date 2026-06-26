import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.serialization)
    id("com.google.devtools.ksp")
    id("androidx.room")
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
            implementation(libs.androidx.sqlite.bundled)
        }
    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }
}

dependencies {
    add("kspCommonMainMetadata", "androidx.room:room-compiler:2.7.2")
    add("kspJvm", "androidx.room:room-compiler:2.7.2")
    add("kspAndroid", "androidx.room:room-compiler:2.7.2")
}

room {
    schemaDirectory("$projectDir/schemas")
}
