plugins {
    alias(libs.plugins.kotlinJvm)
}

val appiumTest =
    sourceSets.create(
        "appiumTest",
    ) {
        java.srcDir("../androidApp/src/appiumTest/kotlin")
    }

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
    testClassesDirs = appiumTest.output.classesDirs
    classpath = appiumTest.runtimeClasspath
    dependsOn(
        ":cloud:server:buildAppiumDockerImage",
        ":cloud:worker:buildAppiumDockerImage",
        ":cloud:cli:buildAppiumDockerImage",
        ":cloud:ws:buildAppiumDockerImage",
        ":panel:androidApp:installDebug",
    )
    maxParallelForks = 1
}
