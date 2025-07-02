plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "com.storyteller_f.a.backend"
version = "unspecified"

dependencies {
    api(libs.exposed.core)
    api(libs.exposed.dao)
    api(libs.exposed.crypt)
    api(libs.exposed.jdbc)
    api(libs.exposed.kotlin.datetime)
    api(libs.exposed.json)
    api(libs.exposed.money)
    implementation(libs.pgjdbc.ng)

    implementation(libs.kotlinx.coroutines.slf4j)
    implementation(projects.backend.core)
    implementation(projects.shared)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
