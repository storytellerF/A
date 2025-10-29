import org.gradle.accessors.dm.LibrariesForLibs
import java.io.FileOutputStream
import java.util.Base64

plugins {
    id("com.android.application")
}
val libs = the<LibrariesForLibs>()
android {
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
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

fun getenv(key: String): String? {
    return System.getenv(key) ?: System.getenv(key.lowercase()) ?: System.getenv(key.uppercase())
}


val decodeBase64ToStoreFileTask = tasks.register("decodeBase64ToStoreFile") {
    group = "signing"
    val signKey = getenv("storyteller_f_sign_key")
    val generatedJksFile = layout.buildDirectory.file("signing/signing_key.jks").get().asFile

    inputs.property("signKey", signKey ?: "")
    outputs.file(generatedJksFile)
    doLast {
        if (!signKey.isNullOrBlank()) {
            // 定义输出文件路径 (如密钥存储文件)
            val outputFile = generatedJksFile

            outputFile.parentFile!!.let {
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
