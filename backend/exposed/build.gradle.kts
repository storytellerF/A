plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "com.storyteller_f.a.backend"
version = "unspecified"

dependencies {
    implementation(libs.napier)
    implementation(libs.bundles.exposed)
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
