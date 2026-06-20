plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(kotlin("test"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":api"))
    implementation(project(":dev:core"))
    implementation(project(":client:core"))
    implementation(project(":shared"))
    implementation(libs.ktor.client.core)
    testImplementation(libs.java.client)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.test {
    dependsOn(
        ":cloud:server:buildAppiumDockerImage",
        ":cloud:worker:buildAppiumDockerImage",
        ":cloud:cli:buildAppiumDockerImage",
        ":cloud:ws:buildAppiumDockerImage",
        ":app:androidApp:installDebug",
        ":panel:androidApp:installDebug",
    )
    maxParallelForks = 1
}
