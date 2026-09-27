/*
 * This is a private project. All rights reserved.
 */

plugins {
    alias(libs.plugins.kotlinJvm)
}

val appiumTest = sourceSets.create("appiumTest")

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(projects.dev.appiumCore)
}

configurations.named(appiumTest.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get())
}

configurations.named(appiumTest.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}

tasks.register<Test>("appiumTest") {
    group = "verification"
    description = "Runs the Panel Android Appium tests."
    outputs.upToDateWhen { false }
    testClassesDirs = appiumTest.output.classesDirs
    classpath = appiumTest.runtimeClasspath
    dependsOn(
        ":cloud:server:distTar",
        ":cloud:worker:distTar",
        ":cloud:cli:distTar",
        ":cloud:ws:distTar",
        ":cloud:filesystem-service:distTar",
        ":cloud:lucene-service:distTar",
        ":panel:androidApp:installDebug",
    )
    maxParallelForks = 1
}
