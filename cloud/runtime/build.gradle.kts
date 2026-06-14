plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.serialization)
}

group = "com.storyteller_f.a.cloud"
version = "unspecified"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.napier)
    implementation(projects.shared)
    implementation(projects.backend.core)
    implementation(projects.cloud.service)
    implementation(libs.endpoint4k.ktor.server)
    implementation(libs.bundles.ktor.server)
    implementation(libs.geoip2)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.collections.immutable)
}
