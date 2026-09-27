/*
 * This is a private project. All rights reserved.
 */

plugins {
    application
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinxRpc)
    alias(libs.plugins.serialization)
}

group = "com.storyteller_f.a.cloud"
version = "unspecified"

application {
    mainClass.set("com.storyteller_f.a.cloud.filesystem.FileSystemServiceKt")
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(projects.backend.core)
    implementation(projects.backend.filesystem)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.kotlinx.rpc.krpc.server)
    implementation(libs.kotlinx.rpc.krpc.ktor.server)
    implementation(libs.kotlinx.rpc.krpc.serialization.json)
}
