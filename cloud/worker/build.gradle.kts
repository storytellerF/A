/*
 * This is a private project. All rights reserved.
 */

plugins {
    application
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.serialization)
    id("cloud")
    id("merge-services")
    alias(libs.plugins.kotlinBuildConfig)
}

group = "com.storyteller_f.a.cloud"
version = "unspecified"

dependencies {
    implementation(libs.napier)
    implementation(projects.backend.core)
    implementation(projects.backend.exposed)
    implementation(projects.cloud.service)
    implementation(projects.cloud.wsApi)
    implementation(projects.shared)
    implementation(libs.litertlm.jvm)
    implementation(libs.koog.core)
    implementation(libs.koog.http.client.ktor)
    implementation(libs.koog.prompt.executor.openai)
    implementation(libs.koog.prompt.executor.openrouter)
    implementation(libs.koog.prompt.executor.anthropic)
    implementation(libs.koog.prompt.executor.ollama)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    // Ktor dependencies for OpenAICompatibleClient
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("mergeServiceFiles") {
    dependsOn(":cloud:ws-api:jar")
}
kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "com.storyteller_f.a.cloud.worker.WorkerMainKt"
    applicationDefaultJvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}
