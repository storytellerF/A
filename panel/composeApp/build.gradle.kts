import com.google.common.base.CaseFormat
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Base64
import java.util.Properties
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
//    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.easylauncher)
    alias(libs.plugins.buildconfig)
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
    }
    if (buildIosTarget) {
        listOf(
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
        js {
            browser()
            binaries.executable()
        }

        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
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

            implementation(libs.androidx.ui.tooling.preview)

            implementation(libs.connector) {
                exclude(group = "com.google.protobuf", module = "protobuf-java")
            }
            implementation(libs.okhttp)
        }
        androidUnitTest.dependencies {
            implementation(libs.androidx.ui.test.junit4.android)
            implementation(libs.androidx.ui.test.manifest)
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
            implementation(compose.materialIconsExtended)
            implementation(compose.material3)
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

            //no ui
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
            //ui
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
            implementation(projects.app.dev)
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
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
        jvmTest {
            dependsOn(headlessTest)
        }
    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xcontext-parameters")
    }
}


composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFiles.addAll(rootProject.layout.projectDirectory.file("stability_config.conf"))
}

android {
    namespace = "org.storyteller_f.a.cloud.panel"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.storyteller_f.a.cloud.panel"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    // 按 ABI 分包
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
    val signPath: String? = getenv("storyteller_f_sign_path")
    val signKey: String? = getenv("storyteller_f_sign_key")
    val signAlias: String? = getenv("storyteller_f_sign_alias")
    val signStorePassword: String? = getenv("storyteller_f_sign_store_password")
    val signKeyPassword: String? = getenv("storyteller_f_sign_key_password")

    signingConfigs {
        val signStorePath = when {
            signPath != null -> File(signPath)
            signKey != null -> layout.buildDirectory.file("signing/signing_key.jks").get().asFile
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
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSignConfig = signingConfigs.findByName("release")
            if (releaseSignConfig != null)
                signingConfig = releaseSignConfig
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        val javaVersion = JavaVersion.forClassVersion(libs.versions.jdk.get().toInt())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    buildFeatures {
        buildConfig = true
    }
    dependencies {
        coreLibraryDesugaring(libs.desugar.jdk.libs)
    }
    lint {
        disable.addAll(arrayOf("RememberReturnType", "UnusedMaterial3ScaffoldPaddingParameter"))
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
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
        mainClass = "org.storyteller_f.a.cloud.panel.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.storyteller_f.a.cloud.panel"
            packageVersion = "1.0.0"
        }
    }
}

buildkonfig {
    packageName = "com.storyteller_f.a.app.compose_app"
    objectName = "AppConfig"
    val properties = Properties().apply {
        val file = layout.projectDirectory.file("../../${flavorStr}.env").asFile
        if (file.exists())
            load(FileInputStream(file))
    }
    val serverUrl = properties["SERVER_URL"] as? String
    val wsServerUrl = properties["WS_SERVER_URL"] as? String
    defaultConfigs {
        buildConfigField(STRING, "SERVER_URL", serverUrl ?: "", const = true)
        buildConfigField(STRING, "WS_SERVER_URL", wsServerUrl ?: "", const = true)
        buildConfigField(STRING, "BUILD_TYPE", buildType, const = true)
        buildConfigField(STRING, "FLAVOR", flavorStr, const = true)
        buildConfigField(BOOLEAN, "ENABLE_LOGIN_CHECK", "false", const = true)
    }
}


val decodeBase64ToStoreFileTask = tasks.register("decodeBase64ToStoreFile") {
    group = "signing"
    val signKey = getenv("storyteller_f_sign_key")
    val generatedJksFile = layout.buildDirectory.file("signing/signing_key.jks").get().asFile

    inputs.property("signKey", signKey)
    outputs.file(generatedJksFile)
    doLast {
        if (!signKey.isNullOrBlank()) {
            // 定义输出文件路径 (如密钥存储文件)
            val outputFile = generatedJksFile

            outputFile.parentFile?.let {
                if (!it.exists() && !it.mkdirs()) {
                    throw Exception("mkdirs failed: $it")
                }
            }
            if (!outputFile.exists() && !outputFile.createNewFile()) {
                throw Exception("create failed: $outputFile")
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

tasks.withType<Test> {
    when (name) {
        "testDebugUnitTest" -> {
            exclude("**/device_based/*")
        }

        "testReleaseUnitTest" -> {
            exclude("**/device_based/*")
        }

        "desktopTest" -> {
            exclude("**/device_based/*")
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.storyteller_f.a.cloud.panel"
    generateResClass = auto
}

if (buildWasmTarget) {
    rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
        rootProject.the<WasmYarnRootExtension>().run {
            lockFileDirectory = project.rootDir.resolve("panel/kotlin-js-store/wasm")
            lockFileName = "panel-yarn.lock"
        }
        rootProject.the<YarnRootExtension>().run {
            lockFileDirectory = project.rootDir.resolve("panel/kotlin-js-store")
            lockFileName = "panel-yarn.lock"
        }
    }
}

private fun KotlinDependencyHandler.implementation(
    dependencyNotation: Provider<MinimalExternalModuleDependency>,
    configure: ExternalModuleDependency.() -> Unit
) {
    implementation(dependencyNotation.get().toString(), configure)
}

fun getenv(key: String): String? {
    return System.getenv(key) ?: System.getenv(key.uppercase())
}
