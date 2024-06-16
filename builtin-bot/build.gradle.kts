plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
    id("com.github.gmazzo.buildconfig") version "5.4.0"
}

group = "com.storyteller_f.a"
version = "1.0.0"
application {
    mainClass.set("com.storyteller_f.a.server.ApplicationKt")
}

dependencies {

}

val isProd = project.findProperty("server.prod") == true

buildConfig {
    buildConfigField<Boolean>("IS_PROD", isProd)
}