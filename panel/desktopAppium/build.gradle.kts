plugins {
    alias(libs.plugins.kotlinJvm)
    id("desktop-appium-agent")
}

val appiumTest =
    sourceSets.create(
        "appiumTest",
    ) {
        java.srcDir("../desktopApp/src/appiumTest/kotlin")
    }

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
    description = "Runs the Panel Desktop Appium tests."
    testClassesDirs = appiumTest.output.classesDirs
    classpath = appiumTest.runtimeClasspath
    dependsOn(
        ":cloud:server:buildAppiumDockerImage",
        ":cloud:worker:buildAppiumDockerImage",
        ":cloud:cli:buildAppiumDockerImage",
        ":cloud:ws:buildAppiumDockerImage",
        ":panel:desktopApp:writeAppiumRuntimeClasspath",
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
