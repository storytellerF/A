plugins {
    `java-library`
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(kotlin("test"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(projects.api)
    api(projects.client.core)
    api(projects.shared)
    api(libs.ktor.client.core)
    api(libs.java.client)
    api(libs.testcontainers.postgresql)
    implementation(libs.selenium.firefox.driver)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.attach"))
}
