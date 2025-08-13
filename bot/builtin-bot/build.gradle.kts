plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
}

group = "com.storyteller_f.a.bot"
version = "1.0.0"
application {
    mainClass.set("com.storyteller_f.a.built_in_bot.BuiltInBotKt")
}

dependencies {

}
