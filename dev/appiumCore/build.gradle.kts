/*
 * This is a private project. All rights reserved.
 */

plugins {
    id("test-artifact-reports")
    `java-library`
    alias(libs.plugins.kotlinJvm)
}

abstract class DesktopAgentArchives {
    @get:javax.inject.Inject
    abstract val archives: org.gradle.api.file.ArchiveOperations
}

kotlin {
    jvmToolchain(21)
}

val desktopBridgeLibraries by configurations.creating

dependencies {
    api(projects.dev.testInfrastructure)
    implementation(libs.asm)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.jna.platform)
    desktopBridgeLibraries(libs.asm)
    api(kotlin("test"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(projects.api)
    api(projects.client.core)
    api(projects.shared)
    api(libs.ktor.client.core)
    api(libs.java.client)
    api(libs.testcontainers)
    api(libs.testcontainers.postgresql)
    api(libs.selenium.support)
    implementation(libs.selenium.chrome.driver)
    implementation(libs.selenium.edge.driver)
    implementation(libs.selenium.firefox.driver)
}

tasks.register<Jar>("desktopWindowsBridgeAgentJar") {
    val archiveService = objects.newInstance<DesktopAgentArchives>().archives
    dependsOn(tasks.named("classes"))
    archiveFileName.set("desktop-windows-bridge-agent.jar")
    destinationDirectory.set(layout.buildDirectory.dir("appium/agent"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Premain-Class" to "com.storyteller_f.a.dev.appium.DesktopWindowsBridgeAgent",
            "Can-Retransform-Classes" to "true",
        )
    }
    from(sourceSets.main.get().output) {
        include(
            "**/DesktopWindowsBridgeAgent*.class",
            "**/DesktopWindowHandles.class",
            "**/DesktopAccessibilityEdt.class",
        )
    }
    from(desktopBridgeLibraries.elements.map { libraries -> libraries.map { archiveService.zipTree(it.asFile) } })
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.attach"))
}

tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED",
    )
}
