@file:Suppress("UnstableApiUsage")

val isAppiumTestEnable = providers.gradleProperty("appium").get() == "true"

rootProject.name = "A"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenLocal()
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
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
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/") {
            content {
                excludeGroup("com.github.umjammer")
                excludeGroup("com.github.cghislai")
            }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        maven {
            name = "github"
            url = uri("https://maven.pkg.github.com/storytellerF/endpoint4k")
            credentials {
                // 需要配置在~/.gradle/gradle.properties
                username = providers.gradleProperty("gpr.user").get()
                password = providers.gradleProperty("gpr.key").get()
            }
            mavenContent {
                includeGroupAndSubgroups("com.storytellerF.endpoint4k")
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
include(":app:android")

include(":dev:core")
include(":dev:server")
include(":dev:cli")
if (isAppiumTestEnable)
    include(":dev:appium")

include(":cloud:server")
include(":cloud:cli")
include(":cloud:worker")
include(":cloud:service")
include(":cloud:pdfbox")
include(":cloud:openpdf")
include(":cloud:pdf")
include(":panel:composeApp")
include(":panel:android")
include(":panel:cliApp")

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

includeBuild("bgscripts")

include(":panel:benchmark")
include(":app:benchmark")