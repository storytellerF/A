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
    implementation(project(":api"))
    implementation(project(":dev:core"))
    implementation(project(":client:core"))
    implementation(project(":shared"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.auth)
    implementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.testcontainers.postgresql)
}
