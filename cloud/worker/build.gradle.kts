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
    implementation(libs.napier)
    implementation(projects.backend.core)
    implementation(projects.backend.exposed)
    implementation(projects.cloud.service)
    implementation(projects.cloud.wsApi)
    implementation(projects.shared)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("mergeServiceFiles") {
    dependsOn(":cloud:ws-api:jar")
}
kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "com.storyteller_f.a.cloud.worker.WorkerMainKt"
    applicationDefaultJvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

val copyAppiumDockerDistribution by tasks.registering(Copy::class) {
    group = "appium"
    description = "Copies the worker distribution used by the Appium Docker image."
    dependsOn(tasks.named("distTar"), tasks.named("distZip"))
    from(layout.buildDirectory.dir("distributions")) {
        include("worker.tar", "worker.zip")
    }
    into(rootProject.layout.projectDirectory.dir("deploy/build"))
}

tasks.register<Exec>("buildAppiumDockerImage") {
    group = "appium"
    description = "Builds the a-worker Docker image used by Appium tests."
    dependsOn(copyAppiumDockerDistribution)
    workingDir = rootProject.layout.projectDirectory.asFile
    commandLine(
        "docker",
        "build",
        "-f",
        "worker.Dockerfile",
        "--build-arg",
        "BUILD_ON=host",
        "-t",
        "a-worker:latest",
        ".",
    )
    outputs.upToDateWhen { false }
}
