plugins {
    kotlin("jvm") version "2.1.20"
}

group = "com.storyteller_f.worker"
version = "unspecified"

dependencies {
    implementation(projects.backend)
    implementation(projects.shared)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}