plugins {
    alias(libs.plugins.kotlinJvm)
    id("desktop-appium-agent")
}

val accessibilityDumpAgentJar = tasks.named<Jar>("accessibilityDumpAgentJar")

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(project(":dev:appiumCore"))
}

tasks.test {
    dependsOn(
        ":cloud:server:buildAppiumDockerImage",
        ":cloud:worker:buildAppiumDockerImage",
        ":cloud:cli:buildAppiumDockerImage",
        ":cloud:ws:buildAppiumDockerImage",
        ":app:desktopApp:writeAppiumRuntimeClasspath",
        accessibilityDumpAgentJar,
    )
    systemProperty("desktop.accessibility.dump.agent", accessibilityDumpAgentJar.flatMap { it.archiveFile }.get().asFile.canonicalPath)
    jvmArgs("--add-modules", "jdk.attach")
    maxParallelForks = 1
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.attach"))
}
