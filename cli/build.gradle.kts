plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

group = "com.storyteller_f"
version = "unspecified"

dependencies {
    implementation(projects.shared)
    testImplementation(kotlin("test"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(projects.backend)
    runtimeOnly(libs.sqlite.jdbc)
    runtimeOnly(libs.slf4j.simple)
    implementation(libs.kotlinx.cli)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.cryptography.provider.jdk)
    implementation(libs.minio)
    implementation(libs.napier)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "com.storyteller_f.MainKt"
}