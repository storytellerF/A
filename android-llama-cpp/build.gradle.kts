plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.storyteller_f.android_llama_cpp"
    compileSdk = 34

    val jllamaLib = file("../java-llama.cpp")

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                // Add a flags if needed
                cppFlags += "-fdeclspec"
                arguments += "-DCMAKE_SOURCE_DIR=${jllamaLib}"
            }
        }
    }
    // Declare c++ sources
    externalNativeBuild {
        cmake {
            path = file("$jllamaLib/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Declare java sources
    sourceSets {
        named("main") {
            // Add source directory for java-llama.cpp
            java.srcDir("$jllamaLib/src/main/java")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(projects.shared)
    implementation(libs.napier)
}