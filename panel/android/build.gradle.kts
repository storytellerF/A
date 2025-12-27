import com.google.common.base.CaseFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidApplication)
//    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.serialization)
    alias(libs.plugins.easylauncher)
    id("compose-app")
}
val buildIosTarget = project.findProperty("target.ios") == "true"
val buildWasmTarget = project.findProperty("target.wasm") == "true"
val flavorStr = project.findProperty("server.flavor") as String
val flavorId = CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.LOWER_UNDERSCORE).convert(flavorStr)!!
val buildType = project.findProperty("server.buildType") as String

android {
    namespace = "com.storyteller_f.a.panel"

    defaultConfig {
        applicationId = "com.storyteller_f.a.panel.$flavorId"
    }
    buildTypes {
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xcontext-parameters")
    }
}

dependencies {
    implementation(projects.app.core)
    implementation(projects.panel.composeApp)
    androidTestImplementation(libs.androidx.ui.test.junit4.android)
    debugImplementation(libs.androidx.ui.test.manifest)
}

easylauncher {
    iconNames.addAll("@mipmap/ic_launcher", "@mipmap/ic_launcher_round")
    buildTypes {
        register("debug") {
            filters(chromeLike(label = flavorStr), greenRibbonFilter("debug"))
        }
        register("release") {
            filters(chromeLike(label = flavorStr))
        }
    }
}
