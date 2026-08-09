@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.google.common.base.CaseFormat
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
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
val flavorStr = project.findProperty("server.flavor") as String
val flavorId = CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.LOWER_UNDERSCORE).convert(flavorStr)!!
val buildType = project.findProperty("server.buildType") as String
val debugBuildTypeName = "debug"

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
            signingConfig = signingConfigs.getByName(debugBuildTypeName)
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
    lint {
        disable.add("Instantiatable")
    }
}

val appiumTestImplementation =
    configurations.create("appiumTestImplementation") {
        isCanBeConsumed = false
        isCanBeResolved = false
    }
val appiumTestRuntimeOnly =
    configurations.create("appiumTestRuntimeOnly") {
        isCanBeConsumed = false
        isCanBeResolved = false
    }
val appiumTestCompileClasspath =
    configurations.create("appiumTestCompileClasspath") {
        isCanBeConsumed = false
        isCanBeResolved = true
        extendsFrom(appiumTestImplementation)
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES))
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
            attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
        }
    }
val appiumTestRuntimeClasspath =
    configurations.create("appiumTestRuntimeClasspath") {
        isCanBeConsumed = false
        isCanBeResolved = true
        extendsFrom(appiumTestImplementation, appiumTestRuntimeOnly)
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
            attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
        }
    }
val appiumTestClasses = layout.buildDirectory.dir("classes/kotlin/appiumTest")
val kotlinBaseApi = plugins.withType<KotlinBaseApiPlugin>().single()
val appiumTestCompilerOptions =
    kotlinBaseApi.createCompilerJvmOptions().apply {
        jvmTarget.set(JvmTarget.JVM_21)
        moduleName.set("app-android-appium-test")
    }
val compileAppiumTestKotlin =
    kotlinBaseApi.registerKotlinJvmCompileTask(
        "compileAppiumTestKotlin",
        appiumTestCompilerOptions,
        providers.provider { ExplicitApiMode.Disabled },
    ).apply {
        configure {
            source("src/appiumTest/kotlin")
            libraries.from(appiumTestCompileClasspath)
            destinationDirectory.set(appiumTestClasses)
            sourceSetName.set("appiumTest")
            multiPlatformEnabled.set(false)
        }
    }

tasks.register<Test>("appiumTest") {
    group = "verification"
    description = "Runs the App Android Appium tests."
    testClassesDirs = files(appiumTestClasses)
    classpath = files(appiumTestClasses, appiumTestRuntimeClasspath)
    dependsOn(
        compileAppiumTestKotlin,
        ":cloud:server:buildAppiumDockerImage",
        ":cloud:worker:buildAppiumDockerImage",
        ":cloud:cli:buildAppiumDockerImage",
        ":cloud:ws:buildAppiumDockerImage",
        ":app:androidApp:installDebug",
    )
    maxParallelForks = 1
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    add(appiumTestImplementation.name, libs.kotlin.test.junit)
    add(appiumTestImplementation.name, projects.dev.appiumCore)

    implementation(projects.client.composeCore)
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
    implementation(libs.connector) {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    debugImplementation(libs.leakcanary.android)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.bundles.ktor.client)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.multiplatform.settings)
    testImplementation(libs.robolectric)
    screenshotTestImplementation(projects.client.composeCore)
    screenshotTestImplementation(projects.app.composeApp)
    screenshotTestImplementation(projects.shared)
    screenshotTestImplementation(projects.client.core)
    screenshotTestImplementation(projects.client.modelStorage)
    screenshotTestImplementation(libs.kotlinx.datetime)
    screenshotTestImplementation(libs.ktor.client.core)
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
    includeNativeSources.set(false)
    includeProguardMapping.set(false)
    includeDependenciesReport.set(false)
    autoUploadNativeSymbols.set(false)
    autoUploadProguardMapping.set(false)
    autoUploadSourceContext.set(false)
}
