@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.google.common.base.CaseFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.easylauncher)
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
        load(FileInputStream(file))
    }
}
val deepLinkHost = (properties["SERVER_URL"] as? String)?.let {
    URI.create(it).host
} ?: "storyteller_f.com"
val deepLinkSchemePrefix = "a-$flavorStr"
android {
    namespace = "com.storyteller_f.a.app"

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
    debugImplementation(libs.leakcanary.android)
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
