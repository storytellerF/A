import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import com.google.common.base.CaseFormat
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.easylauncher)
    alias(libs.plugins.serialization)
    alias(libs.plugins.screenshot)
    id("com.mikepenz.aboutlibraries.plugin")
}

val buildIosTarget = project.findProperty("target.ios") == "true"
val buildWasmTarget = project.findProperty("target.wasm") == "true"
val flavorStr = project.findProperty("buildkonfig.flavor") as String
val flavorId = CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.LOWER_UNDERSCORE).convert(flavorStr)!!
val isProd = project.findProperty("server.prod") == "true"

kotlin {
    if (buildWasmTarget) {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            outputModuleName = "composeApp"
            browser {
                commonWebpackConfig {
                    outputFileName = "composeApp.js"
                    devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                        static = (static ?: mutableListOf()).apply {
                            // Serve sources to debug inside browser
                            add(project.projectDir.path)
                        }
                    }
                }
            }
            binaries.executable()
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }

    jvm("desktop")

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


    sourceSets {
        val desktopMain by getting
        val desktopTest by getting

        androidMain.dependencies {
            implementation(compose.preview)

            implementation(libs.androidx.activity.compose)


            implementation(libs.lifecycle.process)
            implementation(libs.jlatexmath.android)
            implementation(libs.jlatexmath.android.font.cyrillic)
            implementation(libs.jlatexmath.android.font.greek)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.exoplayer.dash)
            implementation(libs.androidx.media3.exoplayer.hls)
            implementation(libs.androidx.media3.ui)
            implementation(libs.androidx.media3.session)
            implementation(libs.record.core)
            implementation(projects.cryptoJvm)
            implementation(libs.github.newpipeextractor)
            implementation(libs.compose.webview)
            implementation(libs.androidx.core.splashscreen)
        }
        androidUnitTest.dependencies {
            implementation(libs.androidx.ui.test.junit4.android)
            implementation(libs.androidx.ui.test.manifest)
            implementation(libs.robolectric)
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
            implementation(libs.navigation.compose)


            api(projects.shared)
            implementation(projects.clientLib)

            implementation(libs.material3.window.size)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            //network
            implementation(libs.bundles.ktor.client)
            implementation(libs.paging.common)
            implementation(libs.paging.compose.common)
            //ui
            implementation(libs.bundles.coil)
            implementation(libs.multiplatform.markdown.renderer)
            implementation(libs.multiplatform.markdown.renderer.m3)
            implementation(libs.multiplatform.markdown.renderer.coil3)
            implementation(libs.multiplatform.markdown.renderer.code)
            implementation(libs.sonner)
            implementation(libs.highlights)
            implementation(libs.richeditor.compose)
            implementation(libs.filekit.compose)
            implementation(libs.compose.pdf)
            implementation(libs.aboutlibraries.core)
            implementation(libs.aboutlibraries.compose.m3)
            implementation(libs.haze)
            implementation(libs.zoomimage.compose.coil3)
            implementation(libs.fonticons.core)
            implementation(libs.krop.ui)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.serialization)
            implementation(libs.couchbase.lite)
            implementation(libs.couchbase.lite.ktx)
            implementation(libs.couchbase.lite.paging)
            implementation(libs.sunny.chung.composable.table)
            implementation(libs.compose.native.notification)

            implementation(libs.kim)
            implementation(libs.napier)
            implementation(libs.uri.kmp)
            implementation(libs.emoji.kt)
            implementation(libs.emoji.compose.m3)
            implementation(libs.m3u.parser)
            implementation(libs.human.readable)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))

            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(projects.testServer.simple)
            implementation(projects.cryptoJvm)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            
            implementation(libs.jlatexmath)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.vlcj)
            implementation(libs.jlayer)
            implementation(projects.cryptoJvm)
        }
        desktopTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFiles.addAll(rootProject.layout.projectDirectory.file("stability_config.conf"))
}

fun getenv(key: String): String? {
    return System.getenv(key) ?: System.getenv(key.uppercase())
}

val signPath: String? = getenv("storyteller_f_sign_path")
val signKey: String? = getenv("storyteller_f_sign_key")
val signAlias: String? = getenv("storyteller_f_sign_alias")
val signStorePassword: String? = getenv("storyteller_f_sign_store_password")
val signKeyPassword: String? = getenv("storyteller_f_sign_key_password")
val generatedJksFile = layout.buildDirectory.file("signing/signing_key.jks").get().asFile
android {
    namespace = "com.storyteller_f.a"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "com.storyteller_f.a.$flavorId"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        val signStorePath = when {
            signPath != null -> File(signPath)
            signKey != null -> generatedJksFile
            else -> null
        }
        if (signStorePath != null && signAlias != null && signStorePassword != null && signKeyPassword != null) {
            create("release") {
                keyAlias = signAlias
                keyPassword = signKeyPassword
                storeFile = signStorePath
                storePassword = signStorePassword
            }
        }
    }
    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }
    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.logging.enabled", true)
            }
        }
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSignConfig = signingConfigs.findByName("release")
            if (releaseSignConfig != null)
                signingConfig = releaseSignConfig
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    dependencies {
        coreLibraryDesugaring(libs.desugar.jdk.libs)
        debugImplementation(compose.uiTooling)
        screenshotTestImplementation(libs.androidx.ui.tooling)
        screenshotTestImplementation(compose.runtime)
    }
    lint {
        disable.add("RememberReturnType")
    }
    @Suppress("UnstableApiUsage")
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

//https://developer.android.com/develop/ui/compose/testing#setup
dependencies {
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

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.storyteller_f.a"
            packageVersion = "1.0.0"
        }
        buildTypes.release.proguard {
            version.set("7.5.0")
            isEnabled = false
//            obfuscate = true
//            optimize = true
            configurationFiles.from("proguard-rules-desktop.pro")
        }
    }
}

buildkonfig {
    packageName = "com.storyteller_f.a.app"
    objectName = "AppConfig"
    val properties = Properties().apply {
        val file = layout.projectDirectory.file("../${flavorStr}.env").asFile
        if (file.exists())
            load(FileInputStream(file))
    }
    val serverUrl = properties["SERVER_URL"] as? String
    val wsServerUrl = properties["WS_SERVER_URL"] as? String
    defaultConfigs {
        buildConfigField(STRING, "PROJECT_PATH", layout.projectDirectory.asFile.absolutePath, const = true)
        buildConfigField(STRING, "SERVER_URL", serverUrl ?: "", const = true)
        buildConfigField(STRING, "WS_SERVER_URL", wsServerUrl ?: "", const = true)
        buildConfigField(BOOLEAN, "IS_PROD", isProd.toString(), const = true)
        buildConfigField(STRING, "FLAVOR", flavorStr, const = true)
    }
}


val decodeBase64ToStoreFileTask = tasks.register("decodeBase64ToStoreFile") {
    group = "signing"
    doLast {
        if (signKey != null) {
            // 定义输出文件路径 (如密钥存储文件)
            val outputFile = generatedJksFile

            outputFile.parentFile?.let {
                if (!it.exists()) {
                    if (!it.mkdirs()) {
                        throw Exception("mkdirs falied: $it")
                    }
                }
            }
            if (!outputFile.exists()) {
                if (!outputFile.createNewFile()) {
                    throw Exception("create failed: $outputFile")
                }
            }
            // 将 Base64 解码为字节
            val decodedBytes = Base64.getDecoder().decode(signKey)

            // 将解码后的字节写入文件
            FileOutputStream(outputFile).use { it.write(decodedBytes) }

            println("Base64 decoded and written to: $outputFile")
        } else {
            println("skip decodeBase64ToStoreFile")
        }

    }

}

afterEvaluate {
    tasks["packageRelease"]?.dependsOn(decodeBase64ToStoreFileTask)
}

aboutLibraries {
    // - If the automatic registered android tasks are disabled, a similar thing can be achieved manually
    // - `./gradlew app:exportLibraryDefinitions -PaboutLibraries.exportPath=src/main/res/raw`
    // - the resulting file can for example be added as part of the SCM
    registerAndroidTasks = false
    // Define the output file name. Modifying this will disable the automatic metadata discovery for supported platforms.
    outputFileName = "aboutlibraries.json"
    // Define the path configuration files are located in. E.g. additional libraries, licenses to add to the target .json
    // Warning: Please do not use the parent folder of a module as path, as this can result in issues. More details: https://github.com/mikepenz/AboutLibraries/issues/936
    configPath = "config"
    // Allow to enable "offline mode", will disable any network check of the plugin (including [fetchRemoteLicense] or pulling spdx license texts)
    offlineMode = true
    // Enable fetching of "remote" licenses.  Uses the API of supported source hosts
    // See https://github.com/mikepenz/AboutLibraries#special-repository-support
    fetchRemoteLicense = true
    // Enables fetching of "remote" funding information. Uses the API of supported source hosts
    // See https://github.com/mikepenz/AboutLibraries#special-repository-support
    fetchRemoteFunding = true
    // (Optional) GitHub token to raise API request limit to allow fetching more licenses
//    gitHubApiToken = getLocalOrGlobalProperty("github.pat")
    // Full license text for license IDs mentioned here will be included, even if no detected dependency uses them.
    additionalLicenses = arrayOf("mit", "mpl_2_0")
    // Allows to exclude some fields from the generated metadata field.
    // If the class name is specified, the field is only excluded for that class; without a class name, the exclusion is global.
    excludeFields = arrayOf("developers", "funding")
    // Enable inclusion of `platform` dependencies in the library report
    includePlatform = true
    // Define the strict mode, will fail if the project uses licenses not allowed
    // - This will only automatically fail for Android projects which have `registerAndroidTasks` enabled
    // For non Android projects, execute `exportLibraryDefinitions`
    strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
    // Allowed set of licenses, this project will be able to use without build failure
//    allowedLicenses = arrayOf("Apache-2.0", "asdkl", "MIT")
    // Enable the duplication mode, allows to merge, or link dependencies which relate
    duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.LINK
    // Configure the duplication rule, to match "duplicates" with
    duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.SIMPLE
    // Enable pretty printing for the generated JSON file
    prettyPrint = true
    // Allows to only collect dependencies of specific variants during the `collectDependencies` step.
    filterVariants = arrayOf("debug", "release")
}

tasks.withType(KotlinCompile::class.java).configureEach {
    dependsOn("exportLibraryDefinitions")
}
tasks.getByName("copyNonXmlValueResourcesForCommonMain").dependsOn("exportLibraryDefinitions")

tasks.withType<Test> {
    when (name) {
        "testDebugUnitTest" -> {
            exclude("**/device_based/*")
        }

        "testReleaseUnitTest" -> {
            exclude("**/device_based/*", "**/jvm_based/*")
        }

        "desktopTest" -> {
            exclude("**/device_based/*")
        }
    }
}
