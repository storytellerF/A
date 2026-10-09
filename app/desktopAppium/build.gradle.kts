/*
 * This is a private project. All rights reserved.
 */

plugins {
    alias(libs.plugins.kotlinJvm)
    id("desktop-appium-agent")
}

val appiumTest = sourceSets.create("appiumTest")
val accessibilityDumpAgentJar = tasks.named<Jar>("accessibilityDumpAgentJar")

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
    description = "Runs the App Desktop Appium tests."
    outputs.upToDateWhen { false }
    testClassesDirs = appiumTest.output.classesDirs
    classpath = appiumTest.runtimeClasspath
    dependsOn(
        ":cloud:server:buildTestDockerImage",
        ":cloud:worker:buildTestDockerImage",
        ":cloud:cli:buildTestDockerImage",
        ":cloud:ws:buildTestDockerImage",
        ":cloud:filesystem-service:buildTestDockerImage",
        ":cloud:lucene-service:buildTestDockerImage",
        ":app:desktopApp:writeAppiumRuntimeClasspath",
        accessibilityDumpAgentJar,
    )
    systemProperty(
        "desktop.accessibility.dump.agent",
        accessibilityDumpAgentJar.flatMap { it.archiveFile }.get().asFile.canonicalPath,
    )
    jvmArgs("--add-modules", "jdk.attach")
    maxParallelForks = 1
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.attach"))
}
