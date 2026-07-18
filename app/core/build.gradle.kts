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
    android {
        namespace = "com.storyteller_f.a.app.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources {
            enable = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        withHostTest { }
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

    sourceSets {
        androidMain {
            kotlin.srcDir("src/jvmAndroidMain/kotlin")
            dependencies {
                implementation(libs.androidx.activity.compose)

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
                implementation(libs.androidx.datastore.preferences.core)

                implementation(libs.androidx.ui.tooling.preview)
                implementation(libs.okhttp)

                implementation(libs.compose.pdf)
                implementation(libs.m3u.parser)
            }
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.robolectric)
            }
        }

        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.material)
            implementation(libs.material3)
            implementation(libs.material.icons.extended)
            implementation(libs.ui)
            implementation(libs.components.resources)
            implementation(libs.ui.tooling.preview)

            implementation(projects.shared)
            implementation(projects.client.core)
            implementation(projects.api)
            implementation(projects.client.modelStorage)
            implementation(projects.client.room)

            implementation(libs.napier)
            implementation(libs.kotlinx.datetime)
            implementation(libs.bundles.ktor.client)
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
            implementation(libs.aboutlibraries.core)
            implementation(libs.aboutlibraries.compose.m3)
            implementation(libs.zoomimage.compose.coil3)
            implementation(libs.fonticons.core)
            implementation(libs.krop.ui)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.serialization)

            implementation(libs.compose.native.notification)
            implementation(libs.compose.preferences)
            implementation(libs.haze)
            implementation(libs.haze.materials)
            implementation(libs.emoji.kt)
            implementation(libs.emoji.compose.m3)
            implementation(libs.connectivity.compose)
            implementation(libs.connectivity.core)

            implementation(libs.kim)
            implementation(libs.uri.kmp)
            implementation(libs.human.readable)
            implementation(libs.kfswatch)

            implementation(libs.latex.base)
            implementation(libs.latex.renderer)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))

            implementation(libs.ui.test)
        }
        jvmMain {
            kotlin.srcDir("src/jvmAndroidMain/kotlin")
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)

                implementation(libs.vlcj)
                implementation(libs.jlayer)
                implementation(libs.androidx.datastore.preferences.core)
                implementation(libs.connectivity.http)
                implementation(libs.connectivity.compose.http)
                implementation(libs.tika.core)

                implementation(libs.compose.pdf)
                implementation(libs.m3u.parser)
            }
        }
    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }
}

dependencies {
    androidRuntimeClasspath(libs.ui.tooling)
    androidRuntimeClasspath(libs.androidx.ui.test.manifest)
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.storyteller_f.a.app.core"
    generateResClass = auto
}
