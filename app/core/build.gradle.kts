import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
}

val buildIosTarget = project.findProperty("target.ios") == "true"
val buildWasmTarget = project.findProperty("target.wasm") == "true"
kotlin {
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

    if (buildWasmTarget) {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            browser()
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate()

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)

            implementation(libs.jlatexmath.android)
            implementation(libs.jlatexmath.android.font.cyrillic)
            implementation(libs.jlatexmath.android.font.greek)

            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.exoplayer.dash)
            implementation(libs.androidx.media3.exoplayer.hls)
            implementation(libs.androidx.media3.ui)
            implementation(libs.androidx.media3.session)
            implementation(libs.lifecycle.service)

            implementation(libs.record.core)
            implementation(libs.compose.webview)
            implementation(libs.connectivity.device)
            implementation(libs.connectivity.compose.device)
            implementation(libs.webrtc.kmp)
            implementation(libs.accompanist.permissions)

            implementation(libs.github.newpipeextractor)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.androidx.datastore.preferences.core)

            implementation(libs.androidx.ui.tooling.preview)
            implementation(libs.okhttp)
        }
        androidUnitTest.dependencies {
            implementation(libs.robolectric)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.navigation.compose)

            implementation(projects.shared)
            implementation(projects.client.core)
            implementation(projects.api)
            implementation(projects.client.modelStorage)
            implementation(projects.client.room)

            implementation(libs.napier)
            implementation(libs.kotlinx.datetime)
            implementation(libs.bundles.ktor.client)
            implementation(libs.tasks.genai)
            // ui
            implementation(libs.material3.window.size)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)
            implementation(libs.bundles.coil)
            implementation(libs.bundles.markdown.render)
            implementation(libs.sonner)
            implementation(libs.highlights)
            implementation(libs.richeditor.compose)
            implementation(libs.bundles.filekit)
            implementation(libs.compose.pdf)
            implementation(libs.aboutlibraries.core)
            implementation(libs.aboutlibraries.compose.m3)
            implementation(libs.zoomimage.compose.coil3)
            implementation(libs.fonticons.core)
            implementation(libs.krop.ui)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.serialization)
            implementation(libs.table.m3)
            implementation(libs.compose.native.notification)
            implementation(libs.compose.preferences)
            implementation(libs.haze)
            implementation(libs.haze.materials)
            implementation(libs.kzip)
            implementation(libs.emoji.kt)
            implementation(libs.emoji.compose.m3)
            implementation(libs.connectivity.compose)
            implementation(libs.connectivity.core)

            implementation(libs.kim)
            implementation(libs.uri.kmp)
            implementation(libs.m3u.parser)
            implementation(libs.human.readable)
            implementation(libs.kfswatch)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))

            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)

            implementation(libs.jlatexmath)
            implementation(libs.vlcj)
            implementation(libs.jlayer)
            implementation(libs.llama)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.connectivity.http)
            implementation(libs.connectivity.compose.http)
            implementation(libs.tika.core)
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
    androidTestImplementation(libs.androidx.ui.test.junit4.android)
    debugImplementation(libs.androidx.ui.test.manifest)
}

android {
    namespace = "com.storyteller_f.a.app.core"
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

compose.resources {
    publicResClass = false
    packageOfResClass = "com.storyteller_f.a.app.core"
    generateResClass = auto
}
