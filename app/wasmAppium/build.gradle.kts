/*
 * This is a private project. All rights reserved.
 */

plugins {
    alias(libs.plugins.kotlinJvm)
}

val appiumTest = sourceSets.create("appiumTest")

val prepareWasmDistribution =
    tasks.register<Sync>("prepareWasmDistribution") {
        dependsOn(":app:webApp:wasmJsBrowserDevelopmentWebpack")
        from(project(":app:webApp").layout.buildDirectory.dir("kotlin-webpack/wasmJs/developmentExecutable"))
        from(rootProject.layout.buildDirectory.file("wasm/packages/appWebApp/kotlin/index.html"))
        from(rootProject.layout.buildDirectory.file("wasm/packages/appWebApp/kotlin/styles.css"))
        from(rootProject.layout.buildDirectory.dir("wasm/packages/appWebApp/kotlin/composeResources")) {
            into("composeResources")
        }
        into(layout.buildDirectory.dir("wasmDistribution"))
    }

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(project(":dev:appiumCore"))
    testImplementation(libs.selenium.firefox.driver)
}

configurations.named(appiumTest.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get())
}

configurations.named(appiumTest.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}

tasks.register<Test>("appiumTest") {
    group = "verification"
    description = "Runs the App Wasm Appium tests."
    outputs.upToDateWhen { false }
    testClassesDirs = appiumTest.output.classesDirs
    classpath = appiumTest.runtimeClasspath
    dependsOn(
        prepareWasmDistribution,
        ":cloud:server:buildAppiumDockerImage",
        ":cloud:worker:buildAppiumDockerImage",
        ":cloud:cli:buildAppiumDockerImage",
        ":cloud:ws:buildAppiumDockerImage",
    )
    jvmArgs("--add-modules", "jdk.httpserver")
    maxParallelForks = 1
}
