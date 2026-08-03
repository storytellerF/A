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
    testImplementation(projects.dev.appiumCore)
    testImplementation(libs.pty4j)
}

tasks.test {
    dependsOn(
        ":cloud:server:buildAppiumDockerImage",
        ":cloud:worker:buildAppiumDockerImage",
        ":cloud:cli:buildAppiumDockerImage",
        ":cloud:ws:buildAppiumDockerImage",
        ":panel:cliApp:installDist",
    )
    systemProperty(
        "panel.cli.app.install.dir",
        project(":panel:cliApp").layout.buildDirectory.dir("install/cliApp").get().asFile.absolutePath,
    )
    maxParallelForks = 1
}
