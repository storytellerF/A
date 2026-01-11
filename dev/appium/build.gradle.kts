plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(kotlin("test"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.java.client)
    implementation(project(":dev:core"))
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.test)
}
