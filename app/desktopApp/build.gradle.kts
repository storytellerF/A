import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(projects.app.composeApp)
    implementation(projects.app.core)
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.native.notification)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.napier)
}

abstract class WriteAppiumRuntimeClasspathTask : DefaultTask() {
    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeClasspath() {
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(classpath.files.joinToString(File.pathSeparator) { it.absolutePath })
        }
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFiles.addAll(rootProject.layout.projectDirectory.file("stability_config.conf"))
}

compose.desktop {
    application {
        mainClass = "com.storyteller_f.a.app.JvmMainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.storyteller_f.a.app"
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

tasks.register<WriteAppiumRuntimeClasspathTask>("writeAppiumRuntimeClasspath") {
    dependsOn(tasks.named("classes"))
    classpath.from(
        layout.buildDirectory.dir("classes/kotlin/main"),
        layout.buildDirectory.dir("resources/main"),
        configurations.named("runtimeClasspath"),
    )
    outputFile.set(layout.buildDirectory.file("appium/runtimeClasspath.txt"))
}
