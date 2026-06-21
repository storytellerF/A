plugins {
    application
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.serialization)
    alias(libs.plugins.kotlinBuildConfig)
    alias(libs.plugins.kotlinxRpc)
    id("cloud")
    id("merge-services")
}

group = "com.storyteller_f.a.cloud"
version = "unspecified"

application {
    mainClass.set("com.storyteller_f.a.cloud.ws.WsApplicationKt")
    applicationDefaultJvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

val copyAppiumDockerDistribution by tasks.registering(Copy::class) {
    group = "appium"
    description = "Copies the ws distribution used by the Appium Docker image."
    dependsOn(tasks.named("distTar"), tasks.named("distZip"))
    from(layout.buildDirectory.dir("distributions")) {
        include("ws.tar", "ws.zip")
    }
    into(rootProject.layout.projectDirectory.dir("deploy/build"))
}

tasks.register<Exec>("buildAppiumDockerImage") {
    group = "appium"
    description = "Builds the a-ws Docker image used by Appium tests."
    dependsOn(copyAppiumDockerDistribution)
    workingDir = rootProject.layout.projectDirectory.asFile
    commandLine(
        "docker",
        "build",
        "-f",
        "ws.Dockerfile",
        "--build-arg",
        "BUILD_ON=host",
        "-t",
        "a-ws:latest",
        ".",
    )
    outputs.upToDateWhen { false }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    implementation(libs.napier)
    implementation(projects.shared)
    implementation(projects.api)
    implementation(projects.backend.core)
    implementation(projects.backend.exposed)
    implementation(projects.cloud.service)
    implementation(projects.cloud.runtime)
    implementation(projects.cloud.wsApi)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.server.client)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.datetime)
    implementation(libs.geoip2)
    implementation(libs.kotlinx.rpc.krpc.server)
    implementation(libs.kotlinx.rpc.krpc.ktor.server)
    implementation(libs.kotlinx.rpc.krpc.serialization.json)
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("mergeServiceFiles") {
    dependsOn(":cloud:ws-api:jar")
    dependsOn(":cloud:runtime:jar")
}
