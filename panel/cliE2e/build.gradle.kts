/*
 * This is a private project. All rights reserved.
 */

plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(projects.dev.e2eCore)
}

tasks.test {
    dependsOn(
        ":cloud:server:buildTestDockerImage",
        ":cloud:worker:buildTestDockerImage",
        ":cloud:cli:buildTestDockerImage",
        ":cloud:ws:buildTestDockerImage",
        ":panel:cliApp:installDist",
    )
    systemProperty(
        "panel.cli.app.install.dir",
        project(":panel:cliApp").layout.buildDirectory.dir("install/cliApp").get().asFile.absolutePath,
    )
    maxParallelForks = 1
}
