plugins {
    application
    alias(libs.plugins.kotlinJvm)
    id("cloud")
    id("merge-services")
    alias(libs.plugins.kotlinBuildConfig)
}

group = "com.storyteller_f.a.cloud"
version = "unspecified"

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)

    implementation(projects.shared)
    implementation(projects.backend.core)
    implementation(projects.backend.exposed)
    implementation(projects.cloud.service)
    implementation(libs.napier)
    implementation(libs.kotlinx.cli)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.cryptography.provider.jdk)
    implementation(libs.tika.core)
    implementation(libs.bundles.ktor.server.client)
    implementation(libs.kotlinx.datetime)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "com.storyteller_f.a.cloud.cli.CliMainKt"
    applicationDefaultJvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}


val copyAppiumDockerDistribution by tasks.registering(Copy::class) {
    group = "appium"
    description = "Copies the cli distribution used by the Appium Docker image."
    dependsOn(tasks.named("distTar"), tasks.named("distZip"))
    from(layout.buildDirectory.dir("distributions")) {
        include("cli.tar", "cli.zip")
    }
    into(rootProject.layout.projectDirectory.dir("deploy/build"))
}

tasks.register<Exec>("buildAppiumDockerImage") {
    group = "appium"
    description = "Builds the a-cli Docker image used by Appium tests."
    dependsOn(copyAppiumDockerDistribution)
    workingDir = rootProject.layout.projectDirectory.asFile
    commandLine(
        "docker",
        "build",
        "-f",
        "cli.Dockerfile",
        "--build-arg",
        "BUILD_ON=host",
        "-t",
        "a-cli:latest",
        ".",
    )
    outputs.upToDateWhen { false }
}
