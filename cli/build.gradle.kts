plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

group = "com.storyteller_f"
version = "unspecified"

dependencies {
    runtimeOnly(libs.sqlite.jdbc)
    runtimeOnly(libs.slf4j.simple)
    testImplementation(kotlin("test"))

    implementation(projects.shared)
    implementation(projects.backend)
    implementation(libs.kotlinx.cli)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.cryptography.provider.jdk)
    implementation(libs.napier)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "com.storyteller_f.MainKt"
}

tasks.withType<JavaExec> {
    jvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}