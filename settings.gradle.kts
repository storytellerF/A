@file:Suppress("UnstableApiUsage")

val isLlamaEnable = providers.gradleProperty("llama.enable").get() == "true"

rootProject.name = "A"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        maven("https://jogamp.org/deployment/maven/")
        maven("https://jitpack.io")
    }
}

include(":shared")

include(":app:composeApp")
include(":app:cliApp")
include(":app:devCli")
include(":app:devServer")

include(":cloud:server")
include(":cloud:cli")
include(":cloud:worker")

include(":backend:service")
include(":backend:exposed")
include(":backend:core")

include(":client:lib")
include(":client:bot-lib")
include(":client:storage")
include(":client:kotbase")

include(":api:core")
include(":api:client")
include(":api:server")

include(":bot:builtin-bot")
if (isLlamaEnable)
include(":android-llama-cpp")
