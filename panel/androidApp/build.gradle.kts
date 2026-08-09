import com.google.common.base.CaseFormat
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.easylauncher)
    alias(libs.plugins.screenshot)
    id("compose-android")
}
val buildIosTarget = project.findProperty("target.ios") == "true"
val flavorStr = project.findProperty("server.flavor") as String
val flavorId = CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.LOWER_UNDERSCORE).convert(flavorStr)!!
val buildType = project.findProperty("server.buildType") as String
val debugBuildTypeName = "debug"

android {
    namespace = "com.storyteller_f.a.panel"
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    defaultConfig {
        applicationId = "com.storyteller_f.a.panel.$flavorId"
    }
    buildTypes {
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
        moduleName.set("panel-android-appium-test")
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
    description = "Runs the Panel Android Appium tests."
    testClassesDirs = files(appiumTestClasses)
    classpath = files(appiumTestClasses, appiumTestRuntimeClasspath)
    dependsOn(
        compileAppiumTestKotlin,
        ":cloud:server:buildAppiumDockerImage",
        ":cloud:worker:buildAppiumDockerImage",
        ":cloud:cli:buildAppiumDockerImage",
        ":cloud:ws:buildAppiumDockerImage",
        ":panel:androidApp:installDebug",
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
    implementation(libs.connectivity.device)
    implementation(libs.connectivity.compose.device)
    implementation(libs.webrtc.kmp)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.connector) {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    implementation(libs.okhttp)

    // 新增依赖 - 从 app/androidApp 复制
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
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.bundles.ktor.client)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.multiplatform.settings)
    testImplementation(libs.robolectric)
    screenshotTestImplementation(projects.client.composeCore)
    screenshotTestImplementation(projects.panel.composeApp)
    screenshotTestImplementation(projects.shared)
    screenshotTestImplementation(projects.client.core)
    screenshotTestImplementation(libs.kotlinx.datetime)
    screenshotTestImplementation(libs.runtime)
    screenshotTestImplementation(libs.foundation)
    screenshotTestImplementation(libs.material3)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.ui.tooling)
    screenshotTestImplementation(libs.jetbrains.navigation3.ui)
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
