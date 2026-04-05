plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(kotlin("test"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.java.client)
    implementation(project(":dev:core"))
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.testcontainers.postgresql)
}
