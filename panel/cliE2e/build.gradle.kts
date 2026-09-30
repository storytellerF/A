/*
 * This is a private project. All rights reserved.
 */

plugins {
    alias(libs.plugins.kotlinJvm)
}

val e2eTest =
    sourceSets.create(
        "e2eTest",
    ) {
        java.srcDir("../cliApp/src/e2eTest/kotlin")
    }

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(projects.dev.e2eCore)
}

configurations.named(e2eTest.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get())
}

configurations.named(e2eTest.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}

tasks.register<Test>("e2eTest") {
    group = "verification"
    description = "Runs the Panel CLI end-to-end tests."
    testClassesDirs = e2eTest.output.classesDirs
    classpath = e2eTest.runtimeClasspath
    dependsOn(
        ":cloud:server:distTar",
        ":cloud:worker:distTar",
        ":cloud:cli:distTar",
        ":cloud:ws:distTar",
        ":cloud:filesystem-service:distTar",
        ":cloud:lucene-service:distTar",
        ":panel:cliApp:installDist",
    )
    systemProperty(
        "panel.cli.app.install.dir",
        project(":panel:cliApp").layout.buildDirectory.dir("install/cliApp").get().asFile.absolutePath,
    )
    maxParallelForks = 1
}
