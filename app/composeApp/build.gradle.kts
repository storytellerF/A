@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import com.google.common.base.CaseFormat
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.internal.de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.easylauncher)
    alias(libs.plugins.serialization)
    id("com.mikepenz.aboutlibraries.plugin")
    id("compose-app")
}

val buildIosTarget = project.findProperty("target.ios") == "true"
val buildWasmTarget = project.findProperty("target.wasm") == "true"
val flavorStr = project.findProperty("server.flavor") as String
val flavorId = CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.LOWER_UNDERSCORE).convert(flavorStr)!!
val buildType = project.findProperty("server.buildType") as String

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }

    if (buildIosTarget) {
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
        }
    }

    jvm()

    if (buildWasmTarget) {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            outputModuleName = "composeApp"
            browser()
            binaries.executable()
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate()

    sourceSets {
        val headlessTest by creating {
            dependsOn(commonTest.get())
        }
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
        }
        androidUnitTest.dependencies {
            implementation(libs.robolectric)
        }
        androidUnitTest {
            dependsOn(headlessTest)
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.leakcanary.android)
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
            implementation(projects.app.core)

            // no ui
            implementation(libs.napier)
            implementation(libs.kotlinx.datetime)
            implementation(libs.bundles.ktor.client)
            implementation(libs.tasks.genai)
            implementation(libs.kim)
            implementation(libs.uri.kmp)
            implementation(libs.m3u.parser)
            implementation(libs.human.readable)
            implementation(libs.kfswatch)
            implementation(libs.kodio.core)
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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))

            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(projects.dev.core)
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
        jvmTest {
            dependsOn(headlessTest)
        }
    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xcontext-parameters")
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
    androidTestImplementation(libs.androidx.ui.test.junit4.android)
    debugImplementation(libs.androidx.ui.test.manifest)
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFiles.addAll(rootProject.layout.projectDirectory.file("stability_config.conf"))
}
val properties = Properties().apply {
    val file = layout.projectDirectory.file("../../$flavorStr.env").asFile
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
            manifestPlaceholders.putAll(mapOf(
                "deepLinkScheme" to "$deepLinkSchemePrefix-debug",
                "deepLinkHost" to deepLinkHost
            ))
        }
        release {
            manifestPlaceholders.putAll(mapOf(
                "deepLinkScheme" to deepLinkSchemePrefix,
                "deepLinkHost" to deepLinkHost
            ))
        }
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
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

compose.desktop {
    application {
        mainClass = "com.storyteller_f.a.app.JvmMainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.storyteller_f.a.app"
            packageVersion = "1.0.0"
        }
        buildTypes.release.proguard {
            version.set("7.5.0")
            isEnabled = false
            obfuscate = true
            optimize = true
            configurationFiles.from("proguard-rules-desktop.pro")
        }
    }
}

buildkonfig {
    packageName = "com.storyteller_f.a.app"
    objectName = "AppConfig"

    val serverUrl = properties["SERVER_URL"] as? String
    val wsServerUrl = properties["WS_SERVER_URL"] as? String
    defaultConfigs {
        buildConfigField(STRING, "SERVER_URL", serverUrl ?: "", const = true)
        buildConfigField(STRING, "WS_SERVER_URL", wsServerUrl ?: "", const = true)
        buildConfigField(STRING, "BUILD_TYPE", buildType, const = true)
        buildConfigField(STRING, "FLAVOR", flavorStr, const = true)
        buildConfigField(BOOLEAN, "ENABLE_LOGIN_CHECK", "false", const = true)
        buildConfigField(STRING, "DEEP_LINK_HOST", deepLinkHost, const = true)
        buildConfigField(STRING, "DEEP_LINK_SCHEME_PREFIX", "a-$flavorStr", const = true)
    }
}

aboutLibraries {
    // Allow to enable "offline mode", will disable any network check of the plugin (including [fetchRemoteLicense] or pulling spdx license texts)
    offlineMode = true

    collect {
        // Define the path configuration files are located in. E.g. additional libraries, licenses to add to the target .json
        // Warning: Please do not use the parent folder of a module as path, as this can result in issues. More details: https://github.com/mikepenz/AboutLibraries/issues/936
        // The path provided is relative to the modules path (not project root)
        configPath = file("../config")

        // (optional) GitHub token to raise API request limit to allow fetching more licenses
        gitHubApiToken = if (hasProperty("github.pat")) property("github.pat")?.toString() else null

        // Enable fetching of "remote" licenses.  Uses the API of supported source hosts
        // See https://github.com/mikepenz/AboutLibraries#special-repository-support
        // A `gitHubApiToken` is required for this to work as it fetches information from GitHub's API.
        fetchRemoteLicense = false

        // Enables fetching of "remote" funding information. Uses the API of supported source hosts
        // See https://github.com/mikepenz/AboutLibraries#special-repository-support
        // A `gitHubApiToken` is required for this to work as it fetches information from GitHub's API.
        fetchRemoteFunding = false

        // Allows to only collect dependencies of specific variants during the `collectDependencies` step.
        // filterVariants.addAll("debug", "release")

        // Enable inclusion of `platform` dependencies in the library report
        includePlatform = true
    }

    export {
        // Define the output path (including fileName). Modifying this will disable the automatic meta data discovery for supported platforms.
        outputFile = file("src/commonMain/composeResources/files/aboutlibraries.json")

        // The default export variant to use for this module.
        // variant = "release"

        // Allows to exclude some fields from the generated meta data field.
        // If the class name is specified, the field is only excluded for that class; without a class name, the exclusion is global.
        excludeFields.addAll("License.name", "developers", "funding")

        // Enable pretty printing for the generated JSON file
        prettyPrint = true
    }

    exports {
        // Define export configuration per variant.
        create("jvm") {
            outputFile = file("files/jvm/aboutlibraries.json")
        }
//        create("wasmJs") {
//            outputFile = file("files/wasmJs/aboutlibraries.json")
//        }
    }

    license {
        // Define the strict mode, will fail if the project uses licenses not allowed
        // - This will only automatically fail for Android projects using the Android-specific plugin (com.mikepenz.aboutlibraries.plugin.android)
        // For other projects, execute `exportLibraryDefinitions` manually
        strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.WARN

        // Allowed set of licenses, this project will be able to use without build failure
        allowedLicenses.addAll(
            "Apache-2.0",
            "asdkl",
            "MIT",
            "BSD-3-Clause",
            "The BSD License",
            "The 3-Clause BSD License"
        )

        // Allowed set of licenses for specific dependencies, this project will be able to use without build failure
        allowedLicensesMap = mapOf(
            "asdkl" to listOf("androidx.jetpack.library"),
            "NOASSERTION" to listOf("org.jetbrains.kotlinx"),
        )

        // Full license text for license IDs mentioned here will be included, even if no detected dependency uses them.
        // additionalLicenses.addAll("mit", "mpl_2_0")
    }

    library {
        // Enable the duplication mode, allows to merge, or link dependencies which relate
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.LINK
        // Configure the duplication rule, to match "duplicates" with
        duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.SIMPLE
    }
}

tasks.withType(KotlinCompile::class.java).configureEach {
    dependsOn("exportLibraryDefinitions")
}
tasks.named("copyNonXmlValueResourcesForCommonMain") {
    dependsOn("exportLibraryDefinitions")
}

tasks.withType<Test> {
    when (name) {
        "testDebugUnitTest" -> {
            exclude("**/device_based/*")
        }

        "testReleaseUnitTest" -> {
            exclude("**/device_based/*")
        }

        "jvmTest" -> {
            exclude("**/device_based/*")
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.storyteller_f.a.app"
    generateResClass = auto
}

if (buildWasmTarget) {
    rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
        rootProject.the<WasmYarnRootExtension>().run {
            lockFileDirectory = project.rootDir.resolve("app/kotlin-js-store/wasm")
            lockFileName = "app-yarn.lock"
        }
        rootProject.the<WasmYarnRootExtension>().run {
            lockFileDirectory = project.rootDir.resolve("app/kotlin-js-store")
            lockFileName = "app-yarn.lock"
        }
    }
}

private fun KotlinDependencyHandler.implementation(
    dependencyNotation: Provider<MinimalExternalModuleDependency>,
    configure: ExternalModuleDependency.() -> Unit
) {
    implementation(dependencyNotation.get().toString(), configure)
}

// Should be run at least once before running the app
val downloadFonts by tasks.registering(Download::class) {
    fun ms(name: String) =
        "https://github.com/google/material-design-icons/raw/${
            "master"
        }/variablefont/MaterialSymbols${
            name.uppercaseFirstChar()
        }%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf" to "material_symbols_$name"

    val fonts = mapOf(ms("outlined"))

    src(fonts.keys)
    dest(layout.projectDirectory.file("src/commonMain/composeResources/font/${fonts.values.first()}.ttf"))
    overwrite(false)
}

tasks.named("copyNonXmlValueResourcesForCommonMain") {
    dependsOn(downloadFonts)
}
