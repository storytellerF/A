plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "com.storyteller_f.a.backend"
version = "unspecified"

dependencies {
    implementation(libs.napier)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(projects.shared)
    implementation(libs.kotlinx.datetime)
    testImplementation(kotlin("test"))
    testImplementation(projects.backend.elastic)
    testImplementation(projects.backend.lucene)
    testImplementation(projects.backend.minio)
    testImplementation(projects.backend.filesystem)
    testImplementation(projects.backend.exposed)
    testImplementation(libs.testcontainers.elasticsearch)
    testImplementation(libs.testcontainers.minio)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.h2)
    testImplementation(libs.postgresql)
    runtimeOnly(libs.vavi.image.avif)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
