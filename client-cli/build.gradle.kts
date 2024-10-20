plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    application
}
application {
    mainClass.set("com.storyteller_f.client_cli.MainKt")
}
dependencies {
    implementation(libs.mosaic.runtime)
}
