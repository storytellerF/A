import com.google.common.base.CaseFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.serialization)
    alias(libs.plugins.easylauncher)
    id("compose-android")
}
val buildIosTarget = project.findProperty("target.ios") == "true"
val buildWasmTarget = project.findProperty("target.wasm") == "true"
val flavorStr = project.findProperty("server.flavor") as String
val flavorId = CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.LOWER_UNDERSCORE).convert(flavorStr)!!
val buildType = project.findProperty("server.buildType") as String

android {
    namespace = "com.storyteller_f.a.panel"

    defaultConfig {
        applicationId = "com.storyteller_f.a.panel.$flavorId"
    }
    buildTypes {
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xcontext-parameters")
    }
}

dependencies {
    implementation(projects.app.core)
    implementation(projects.panel.composeApp)
    implementation(projects.shared)
    implementation(projects.client.core)

    // 新增依赖 - 从 composeApp/androidMain 复制
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.lifecycle.service)
    implementation(libs.compose.webview)
    implementation(libs.connectivity.device)
    implementation(libs.connectivity.compose.device)
    implementation(libs.webrtc.kmp)
    implementation(libs.accompanist.permissions)
    implementation(libs.github.newpipeextractor)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.connector) {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    implementation(libs.okhttp)

    // 新增依赖 - 从 app/android 复制
    implementation(libs.runtime)
    implementation(libs.foundation)
    implementation(libs.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.bundles.filekit)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.napier)

    androidTestImplementation(libs.androidx.ui.test.junit4.android)
    debugImplementation(libs.androidx.ui.test.manifest)
}

easylauncher {
    iconNames.addAll("@mipmap/ic_launcher", "@mipmap/ic_launcher_round")
    buildTypes {
        register("debug") {
            filters(chromeLike(label = flavorStr), greenRibbonFilter("debug"))
        }
        register("release") {
            filters(chromeLike(label = flavorStr))
        }
    }
}
