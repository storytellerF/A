import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    id("appium-runtime-classpath")
    id("desktop-appium-agent")
}

val appiumTest = sourceSets.create("appiumTest")
val accessibilityDumpAgentJar = tasks.named<Jar>("accessibilityDumpAgentJar")

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(projects.panel.composeApp)
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.napier)

    add(appiumTest.implementationConfigurationName, libs.kotlin.test.junit)
    add(appiumTest.implementationConfigurationName, libs.runtime)
    add(appiumTest.implementationConfigurationName, projects.dev.appiumCore)
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFiles.addAll(rootProject.layout.projectDirectory.file("stability_config.conf"))
}

compose.desktop {
    application {
        mainClass = "com.storyteller_f.a.panel.PanelMainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.storyteller_f.a.panel"
            packageVersion = "1.0.0"
        }
        buildTypes.release.proguard {
            version.set("7.5.0")
            isEnabled = false
            obfuscate = true
            optimize = true
            configurationFiles.from(file("proguard-rules-desktop.pro"))
        }
    }
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
