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
include(":app:webApp")
include(":app:cliApp")
include(":app:androidApp")
include(":app:desktopApp")

if (isAppiumTestEnable) {
    include(":dev:appiumCore")
    include(":app:androidAppium")
    include(":app:cliAppium")
    include(":app:desktopAppium")
    include(":app:wasmAppium")
    include(":panel:androidAppium")
    include(":panel:cliAppium")
    include(":panel:desktopAppium")
    include(":panel:wasmAppium")
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
include(":panel:webApp")
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
include(":client:composeCore")
include(":client:model-storage")
//include(":client:kotbase")
include(":client:room")
//include(":client:sqlite-now")
include(":client:asciidoc-parser")

include(":api")

include(":bot:builtin-bot")

include(":panel:benchmark")
include(":app:benchmark")

includeBuild("bgscripts")
