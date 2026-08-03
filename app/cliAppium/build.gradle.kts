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
        ":app:cliApp:installDist",
    )
    systemProperty(
        "cli.app.install.dir",
        project(":app:cliApp").layout.buildDirectory.dir("install/cliApp").get().asFile.absolutePath,
    )
    maxParallelForks = 1
}
