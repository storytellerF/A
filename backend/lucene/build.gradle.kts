plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinBuildConfig)
}

group = "com.storyteller_f.a.backend"
version = "unspecified"

dependencies {
    implementation(libs.napier)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(projects.backend.core)
    implementation(projects.shared)
    implementation(libs.bundles.lucene)
    implementation(libs.memoryfilesystem)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}