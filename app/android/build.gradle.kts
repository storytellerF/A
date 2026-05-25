@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.google.common.base.CaseFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.easylauncher)
    alias(libs.plugins.screenshot)
    alias(libs.plugins.serialization)
    id("compose-android")
    id("io.sentry.android.gradle")
}

val buildIosTarget = project.findProperty("target.ios") == "true"
val buildWasmTarget = project.findProperty("target.wasm") == "true"
val flavorStr = project.findProperty("server.flavor") as String
val flavorId = CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.LOWER_UNDERSCORE).convert(flavorStr)!!
val buildType = project.findProperty("server.buildType") as String

val properties = Properties().apply {
    val file = layout.projectDirectory.file("../../deploy/$flavorStr.env").asFile
    if (file.exists()) {
        FileInputStream(file).use {
            load(it)
        }
    }
}
val deepLinkHost = (properties["SERVER_URL"] as? String)?.let {
    URI.create(it).host
} ?: "storyteller_f.com"
val deepLinkSchemePrefix = "a-$flavorStr"
android {
    namespace = "com.storyteller_f.a.app"
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    defaultConfig {
        applicationId = "com.storyteller_f.a.app.$flavorId"
    }
    buildTypes {
        debug {
            manifestPlaceholders.putAll(
                mapOf(
                    "deepLinkScheme" to "$deepLinkSchemePrefix-debug",
                    "deepLinkHost" to deepLinkHost
                )
            )
        }
        release {
            manifestPlaceholders.putAll(
                mapOf(
                    "deepLinkScheme" to deepLinkSchemePrefix,
                    "deepLinkHost" to deepLinkHost
                )
            )
        }
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
    lint {
        disable.add("Instantiatable")
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
    implementation(projects.app.composeApp)
    implementation(projects.shared)
    implementation(projects.client.core)
    implementation(libs.runtime)
    implementation(libs.foundation)
    implementation(libs.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.media3.session)
    implementation(libs.bundles.filekit)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.napier)
    implementation(libs.webrtc.kmp)
    implementation(libs.github.newpipeextractor)
    implementation(libs.connector) {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    debugImplementation(libs.leakcanary.android)
    screenshotTestImplementation(projects.app.core)
    screenshotTestImplementation(projects.app.composeApp)
    screenshotTestImplementation(projects.shared)
    screenshotTestImplementation(projects.client.core)
    screenshotTestImplementation(projects.client.modelStorage)
    screenshotTestImplementation(libs.kotlinx.datetime)
    screenshotTestImplementation(libs.runtime)
    screenshotTestImplementation(libs.foundation)
    screenshotTestImplementation(libs.material3)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.ui.tooling)
    screenshotTestImplementation(libs.jetbrains.navigation3.ui)
    screenshotTestImplementation(libs.material.icons.extended)
}

easylauncher {
    showWarnings.set(true)
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

sentry {
    org.set("acommunity")
    projectName.set("android")

    // this will upload your source code to Sentry to show it as part of the stack traces
    // disable if you don't want to expose your sources
    includeSourceContext.set(false)
}
