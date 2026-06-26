import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
