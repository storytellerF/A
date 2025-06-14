plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
    alias(libs.plugins.kotlinBuildConfig)
}

group = "com.storyteller_f.a"
version = "1.0.0"
application {
    mainClass.set("com.storyteller_f.a.built_in_bot.BuiltInBotKt")
}

dependencies {

}

val buildType = project.findProperty("server.buildType") as String

buildConfig {
    buildConfigField<String>("BUILD_TYPE", buildType)
}