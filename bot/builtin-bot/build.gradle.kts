plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

group = "com.storyteller_f.a.bot"
version = "1.0.0"
application {
    mainClass.set("com.storyteller_f.a.built_in_bot.BuiltInBotKt")
}

dependencies {
    implementation(libs.napier)
    implementation(projects.shared)
    implementation(projects.client.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
}
