/*
 * This is a private project. All rights reserved.
 */

plugins {
    application
}

val distribution = tasks.named<Tar>("distTar")
val dockerContext = layout.buildDirectory.dir("test-docker-context")
val copyTestDockerDistribution = tasks.register<Sync>("copyTestDockerDistribution") {
    group = "verification"
    description = "Prepares an isolated context for this service's prebuilt test image."
    from(distribution.flatMap { it.archiveFile }) {
        into("deploy/build")
    }
    from(rootProject.layout.projectDirectory.file("Dockerfile"))
    from(rootProject.layout.projectDirectory.dir("scripts")) {
        include(
            "build_scripts/stage-distributions.sh", "docker/cli-entrypoint.sh",
            "tool_scripts/flush-database.sh", "tool_scripts/terminal-log.sh",
        )
        into("scripts")
    }
    into(dockerContext)
}

val imageName = project.name.removeSuffix("-service")
tasks.register<Exec>("buildTestDockerImage") {
    group = "verification"
    description = "Builds the test service image with an isolated context and the root Dockerfile."
    dependsOn(copyTestDockerDistribution)
    workingDir(dockerContext)
    commandLine(
        "docker", "build", "--file", "Dockerfile", "--target", project.name,
        "--build-arg", "BUILDER_STAGE=prebuilt-builder", "--tag", "a-$imageName:latest", ".",
    )
    outputs.upToDateWhen { false }
}
