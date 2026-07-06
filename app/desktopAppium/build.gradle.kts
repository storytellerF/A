plugins {
    alias(libs.plugins.kotlinJvm)
}

val accessibilityDumpAgentJar by tasks.registering(Jar::class) {
    archiveFileName.set("desktop-accessibility-dump-agent.jar")
    destinationDirectory.set(layout.buildDirectory.dir("appium/agent"))
    manifest {
        attributes(
            "Agent-Class" to "DesktopAccessibilityDumpAgent",
            "Can-Redefine-Classes" to "false",
            "Can-Retransform-Classes" to "false",
        )
    }
    from(tasks.named("compileTestJava")) {
        include("DesktopAccessibilityDumpAgent*.class")
    }
}

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
