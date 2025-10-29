plugins {
    kotlin("jvm")
    application
    id("cloud")
    id("merge-services")
    alias(libs.plugins.kotlinBuildConfig)
}

group = "com.storyteller_f.a.cloud"
version = "unspecified"

dependencies {
    implementation(libs.napier)
    implementation(projects.backend.core)
    implementation(projects.backend.exposed)
    implementation(projects.cloud.service)
    implementation(projects.shared)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "com.storyteller_f.a.cloud.worker.WorkerMainKt"
    applicationDefaultJvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

val flavor = project.findProperty("server.flavor").toString()

buildConfig {
    className = "BackendConfig"
    buildConfigField<String>("FLAVOR", flavor)
}
