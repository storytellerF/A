plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinBuildConfig)
}

group = "com.storyteller_f.a.http.core"
version = "unspecified"

dependencies {
    implementation(libs.ktor.server.core)
    implementation(projects.shared)
    implementation(projects.http.core)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
