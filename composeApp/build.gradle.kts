import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import com.google.common.base.CaseFormat
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    id("com.codingfeline.buildkonfig") version "0.15.1"
    id("com.starter.easylauncher") version "6.4.0"
}

val buildIosTarget = project.findProperty("target.ios") == true
val buildWasmTarget = project.findProperty("target.wasm") == true
val flavor = project.findProperty("buildkonfig.flavor") as String
val flavorTaskName = CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.LOWER_CAMEL).convert(flavor)!!
val flavorId = CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.LOWER_UNDERSCORE).convert(flavor)!!
val isProd = project.findProperty("server.prod") == true

kotlin {
    if (buildWasmTarget) {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            moduleName = "composeApp"
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
//        @OptIn(ExperimentalKotlinGradlePluginApi::class)
//        compilerOptions {
//            jvmTarget.set(JvmTarget.JVM_11)
//        }
        compilations.all { kotlinOptions { jvmTarget = "11" } }
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

        androidMain.dependencies {
            implementation(compose.preview)

            implementation(libs.androidx.activity.compose)

            implementation(libs.emoji.reader.jvm)
            implementation(libs.lifecycle.process)
            implementation(libs.jlatexmath.android)
            implementation(libs.jlatexmath.android.font.cyrillic)
            implementation(libs.jlatexmath.android.font.greek)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(projects.shared)
            implementation(projects.clientLib)

            implementation(libs.material3.window.size)
            implementation(libs.precompose)
            implementation(libs.precompose.viewmodel)
            //network
            implementation(libs.bundles.ktor.client)
            implementation(libs.paging.common)
            implementation(libs.paging.compose.common)
            //ui
            implementation(libs.bundles.coil)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.multiplatform.settings.serialization)
            implementation(libs.napier)
            implementation(libs.couchbase.lite)
            implementation(libs.couchbase.lite.ktx)
            implementation(libs.couchbase.lite.paging)
            implementation(libs.multiplatform.markdown.renderer)
            implementation(libs.multiplatform.markdown.renderer.m3)
            implementation(libs.multiplatform.markdown.renderer.coil3)
            implementation(libs.uri.kmp)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.emoji.reader.jvm)
            implementation(libs.jlatexmath)
        }
    }
}

val signPath: String? = System.getenv("storyteller_f_sign_path")
val signKey: String? = System.getenv("storyteller_f_sign_key")
val signAlias: String? = System.getenv("storyteller_f_sign_alias")
val signStorePassword: String? = System.getenv("storyteller_f_sign_store_password")
val signKeyPassword: String? = System.getenv("storyteller_f_sign_key_password")
val generatedJksFile = layout.buildDirectory.file("signing/signing_key.jks").get().asFile
android {
    namespace = "com.storyteller_f.a"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "com.storyteller_f.a"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        val signStorePath = if (signPath != null) {
            File(signPath)
        } else if (signKey != null) {
            generatedJksFile
        } else {
            null
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
    flavorDimensions += "server"
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".$flavorId.debug"
        }
        getByName("release") {
            applicationIdSuffix = ".$flavorId"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSignConfig = signingConfigs.findByName("release")
            if (releaseSignConfig != null)
                signingConfig = releaseSignConfig
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    dependencies {
        debugImplementation(compose.uiTooling)
    }
}

easylauncher {
    buildTypes {
        register("debug") {
            filters(chromeLike(label = flavor), greenRibbonFilter("debug"))
        }
        register("release") {
            filters(chromeLike(label = flavor))
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
    }
}

buildkonfig {
    packageName = "com.storyteller_f.a.app"
    val properties = Properties().apply {
        val file = layout.projectDirectory.file("../${flavor}.env").asFile
        load(FileInputStream(file))
    }
    val serverUrl = properties["SERVER_URL"] as String
    val wsServerUrl = properties["WS_SERVER_URL"] as String
    defaultConfigs {
        buildConfigField(STRING, "PROJECT_PATH", layout.projectDirectory.asFile.absolutePath, const = true)
        buildConfigField(
            STRING,
            "GITHUB_CARD_LINK",
            "https://github-link-card.s3.ap-northeast-1.amazonaws.com/storytellerF/A.png", const = true
        )
        buildConfigField(STRING, "SERVER_URL", serverUrl, const = true)
        buildConfigField(STRING, "WS_SERVER_URL", wsServerUrl, const = true)
        buildConfigField(BOOLEAN, "IS_PROD", isProd.toString(), const = true)
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
