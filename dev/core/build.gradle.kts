plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "com.storyteller_f.a.app"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.napier)
    implementation(libs.kotlinx.coroutines.core)
}