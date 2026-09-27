/*
 * This is a private project. All rights reserved.
 */

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinxRpc)
    alias(libs.plugins.serialization)
}

group = "com.storyteller_f.a.backend"
version = "unspecified"

dependencies {
    implementation(libs.napier)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.rpc.core)
    implementation(projects.shared)
    implementation(libs.kotlinx.datetime)
    testImplementation(kotlin("test"))
    testImplementation(projects.backend.elastic)
    testImplementation(projects.backend.lucene)
    testImplementation(projects.backend.minio)
    testImplementation(projects.backend.filesystem)
    testImplementation(projects.backend.exposed)
    testImplementation(projects.backend.simple)
    testImplementation(libs.testcontainers.elasticsearch)
    testImplementation(libs.testcontainers.minio)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.h2)
    testImplementation(libs.postgresql)
    runtimeOnly(libs.image.avif)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
