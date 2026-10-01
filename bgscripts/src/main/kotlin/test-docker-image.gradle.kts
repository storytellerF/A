/*
 * This is a private project. All rights reserved.
 */

plugins {
    application
}

val distribution = tasks.named<Tar>("distTar")
val dockerImageBuildService = gradle.sharedServices.registerIfAbsent(
    "docker-image-build", DockerImageBuildService::class,
) {
    maxParallelUsages = 1
}
val copyTestDockerDistribution = tasks.register<Copy>("copyTestDockerDistribution") {
    group = "verification"
    description = "Copies this service distribution into the Docker build context."
    // Do not mutate the shared context while another service is archiving it.
    usesService(dockerImageBuildService)
    from(distribution.flatMap { it.archiveFile })
    into(rootProject.layout.projectDirectory.dir("deploy/build"))
}

val imageName = project.name.removeSuffix("-service")
tasks.register<Exec>("buildTestDockerImage") {
    group = "verification"
    description = "Builds the test service image with the root Dockerfile."
    dependsOn(copyTestDockerDistribution)
    usesService(dockerImageBuildService)
    workingDir = rootProject.layout.projectDirectory.asFile
    commandLine(
        "docker", "build", "--file", "Dockerfile", "--target", project.name,
        "--build-arg", "BUILD_ON=local", "--tag", "a-$imageName:latest", ".",
    )
    // Image existence belongs to Docker, not Gradle's file-output cache.
    outputs.upToDateWhen { false }
}
