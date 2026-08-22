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

val copyTestDockerDistribution =
    tasks.register<Copy>("copyTestDockerDistribution") {
        group = "verification"
        description = "Copies the worker distribution used by test Docker images."
        dependsOn(tasks.named("distTar"), tasks.named("distZip"))
        from(layout.buildDirectory.dir("distributions")) {
            include("worker.tar", "worker.zip")
        }
        into(rootProject.layout.projectDirectory.dir("deploy/build"))
    }

val buildTestDockerImage =
    tasks.register<Exec>("buildTestDockerImage") {
        group = "verification"
        description = "Builds the a-worker Docker image used by integration tests."
        dependsOn(copyTestDockerDistribution)
        workingDir = rootProject.layout.projectDirectory.asFile
        commandLine(
            "docker",
            "build",
            "-f",
            "worker.Dockerfile",
            "--build-arg",
            "BUILD_ON=host",
            "-t",
            "a-worker:latest",
            ".",
        )
        outputs.upToDateWhen { false }
    }

tasks.register("buildAppiumDockerImage") {
    group = "appium"
    description = "Compatibility alias for buildTestDockerImage."
    dependsOn(buildTestDockerImage)
}
