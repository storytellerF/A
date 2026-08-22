/*
 * This is a private project. All rights reserved.
 */

plugins {
    application
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.serialization)
    alias(libs.plugins.kotlinBuildConfig)
    alias(libs.plugins.kotlinxRpc)
    id("cloud")
    id("merge-services")
}

group = "com.storyteller_f.a.cloud"
version = "unspecified"

application {
    mainClass.set("com.storyteller_f.a.cloud.ws.WsApplicationKt")
    applicationDefaultJvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

val copyTestDockerDistribution =
    tasks.register<Copy>("copyTestDockerDistribution") {
        group = "verification"
        description = "Copies the ws distribution used by test Docker images."
        dependsOn(tasks.named("distTar"), tasks.named("distZip"))
        from(layout.buildDirectory.dir("distributions")) {
            include("ws.tar", "ws.zip")
        }
        into(rootProject.layout.projectDirectory.dir("deploy/build"))
    }

val buildTestDockerImage =
    tasks.register<Exec>("buildTestDockerImage") {
        group = "verification"
        description = "Builds the a-ws Docker image used by integration tests."
        dependsOn(copyTestDockerDistribution)
        workingDir = rootProject.layout.projectDirectory.asFile
        commandLine(
            "docker",
            "build",
            "-f",
            "ws.Dockerfile",
            "--build-arg",
            "BUILD_ON=host",
            "-t",
            "a-ws:latest",
            ".",
        )
        outputs.upToDateWhen { false }
    }

tasks.register("buildAppiumDockerImage") {
    group = "appium"
    description = "Compatibility alias for buildTestDockerImage."
    dependsOn(buildTestDockerImage)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.napier)
    implementation(projects.shared)
    implementation(projects.api)
    implementation(projects.backend.core)
    implementation(projects.backend.exposed)
    implementation(projects.cloud.service)
    implementation(projects.cloud.runtime)
    implementation(projects.cloud.wsApi)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.server.client)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.datetime)
    implementation(libs.geoip2)
    implementation(libs.kotlinx.rpc.krpc.server)
    implementation(libs.kotlinx.rpc.krpc.ktor.server)
    implementation(libs.kotlinx.rpc.krpc.serialization.json)
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("mergeServiceFiles") {
    dependsOn(":cloud:ws-api:jar")
    dependsOn(":cloud:runtime:jar")
}
