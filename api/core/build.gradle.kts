plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinBuildConfig)
    alias(libs.plugins.serialization)
}

group = "com.storyteller_f.a.api"
version = "unspecified"

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(projects.shared)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
