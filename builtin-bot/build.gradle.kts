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

val isProd = project.findProperty("server.prod") == true

buildConfig {
    buildConfigField<Boolean>("IS_PROD", isProd)
}