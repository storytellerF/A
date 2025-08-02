plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "com.storyteller_f.a.backend"
version = "unspecified"

dependencies {
    implementation(projects.shared)
    implementation(libs.kotlinx.datetime)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
