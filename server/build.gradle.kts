plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.serialization)
    application
    alias(libs.plugins.kotlinBuildConfig)
}

group = "com.storyteller_f.a"
version = "1.0.0"
application {
    mainClass.set("com.storyteller_f.a.server.ApplicationKt")
    applicationDefaultJvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    runtimeOnly(libs.logback)

    implementation(libs.cryptography.provider.jdk)
    implementation(projects.shared)
    implementation(projects.backend)
    implementation(libs.bundles.ktor.server)
    implementation(libs.pdfbox)
    implementation(libs.napier)
    implementation(libs.emoji.reader.jvm)
    implementation(libs.geoip2)

    testImplementation(projects.clientLib)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.h2)
    testImplementation(libs.testcontainers.minio)
    testImplementation(libs.elasticsearch)
    testImplementation(libs.testcontainers.postgresql)
}

val isProd = project.findProperty("server.prod") == true

buildConfig {
    buildConfigField<Boolean>("IS_PROD", isProd)
}

tasks.withType<JavaExec> {
    jvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}