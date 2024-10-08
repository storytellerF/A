plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.serialization)
    application
    id("com.github.gmazzo.buildconfig") version "5.4.0"
}

group = "com.storyteller_f.a"
version = "1.0.0"
application {
    mainClass.set("com.storyteller_f.a.server.ApplicationKt")
}

dependencies {
    implementation(libs.cryptography.provider.jdk)
    implementation(projects.shared)
    implementation(projects.backend)
    runtimeOnly(libs.logback)
    implementation(libs.bundles.ktor.server)
    implementation(libs.minio)

    testImplementation(projects.clientLib)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.h2)
    implementation(libs.ktoml.core)
    testImplementation(libs.jimfs)
}

val isProd = project.findProperty("server.prod") == true

buildConfig {
    buildConfigField<Boolean>("IS_PROD", isProd)
}
