plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    application
}
application {
    mainClass.set("example.MainKt")
}
dependencies {
    implementation(libs.mosaic.runtime)
}
