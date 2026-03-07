plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.serialization)
    application
    alias(libs.plugins.kotlinxRpc)
}

group = "com.storyteller_f.a.dev.cli"

application {
    mainClass.set("com.storyteller_f.a.app.dev_cli.CliMainKt")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.logback)
    implementation(libs.napier)
    implementation(libs.bundles.ktor.server.client)
    implementation(libs.kotlinx.cli)
    implementation(projects.dev.core)
    implementation(libs.kotlinx.rpc.krpc.client)
    implementation(libs.kotlinx.rpc.krpc.ktor.client)
    implementation(libs.kotlinx.rpc.krpc.serialization.json)
}
