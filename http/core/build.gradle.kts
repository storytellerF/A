plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinBuildConfig)
}

group = "com.storyteller_f.a.http.core"
version = "unspecified"

dependencies {
    implementation(projects.shared)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
