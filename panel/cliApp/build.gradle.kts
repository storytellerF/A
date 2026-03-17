plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    application
}

group = "com.storyteller_f.a.panel"
version = "1.0.0"

application {
    mainClass.set("com.storyteller_f.a.panel_cli_app.MainKt")
}
dependencies {
    implementation(libs.mosaic.runtime)
    implementation(libs.napier)
    implementation(projects.client.core)
    implementation(projects.api)
    implementation(projects.shared)
    implementation(libs.bundles.ktor.client)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.collections.immutable)
}
tasks.withType<JavaExec> {
    standardInput = System.`in`
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
