@file:Suppress("UnstableApiUsage")

val isAppiumTestEnable = providers.gradleProperty("appium").get() == "true"

rootProject.name = "A"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenLocal()
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
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
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
include(":app:core")
include(":app:androidApp")
include(":app:desktopApp")

if (isAppiumTestEnable) {
    include(":dev:appiumCore")
    include(":app:androidAppium")
    include(":app:desktopAppium")
    include(":panel:androidAppium")
    include(":panel:desktopAppium")
}

include(":cloud:server")
include(":cloud:runtime")
include(":cloud:ws-api")
include(":cloud:ws")
include(":cloud:cli")
include(":cloud:worker")
include(":cloud:service")
include(":cloud:pdfbox")
include(":cloud:openpdf")
include(":cloud:pdf")
include(":panel:composeApp")
include(":panel:androidApp")
include(":panel:cliApp")
include(":panel:desktopApp")

include(":backend:exposed")
include(":backend:core")
include(":backend:lucene")
include(":backend:elastic")
include(":backend:simple")
include(":backend:filesystem")
include(":backend:redis")
include(":backend:minio")

include(":client:core")
include(":client:bot-lib")
include(":client:model-storage")
//include(":client:kotbase")
include(":client:room")
//include(":client:sqlite-now")
include(":client:ascii-parser")

include(":api")

include(":bot:builtin-bot")

include(":panel:benchmark")
include(":app:benchmark")

includeBuild("bgscripts")
