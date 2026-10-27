/*
 * This is a private project. All rights reserved.
 */

import org.apache.tools.ant.filters.FixCrLfFilter

plugins {
    application
}

val coverageEnabled = providers.gradleProperty("e2e.coverage").map(String::toBooleanStrict).orElse(false)
val distribution = tasks.named<Tar>("distTar")
val imageName = project.name.removeSuffix("-service")
val serviceName = project.name
val variants = listOf("Test" to "latest") +
    if (coverageEnabled.get()) listOf("E2eCoverage" to "e2e-coverage") else emptyList()

variants.forEach { (prefix, tag) ->
    val covered = tag == "e2e-coverage"
    val distributionTaskName = if (covered) "e2eCoverageDistTar" else "distTar"
    val distributionArchive = if (covered) {
        layout.buildDirectory.file("e2e-coverage/distributions/$serviceName.tar")
    } else {
        distribution.flatMap { it.archiveFile }
    }
    val dockerContext = layout.buildDirectory.dir(
        if (covered) "e2e-coverage/test-docker-context" else "test-docker-context",
    )
    val copied = tasks.register<Sync>("copy${prefix}DockerDistribution") {
        group = "verification"
        description = "Prepares an isolated context for the $tag service test image."
        dependsOn(distributionTaskName)
        val shellScriptLineEnding = "lf"
        inputs.property("shellScriptLineEnding", shellScriptLineEnding)
        from(distributionArchive) { into("deploy/build") }
        from(rootProject.layout.projectDirectory.file("Dockerfile"))
        from(rootProject.layout.projectDirectory.dir("scripts")) {
            include(
                "build_scripts/stage-distributions.sh", "docker/cli-entrypoint.sh",
                "tool_scripts/flush-database.sh", "tool_scripts/terminal-log.sh",
            )
            // Linux containers must not inherit CRLF from a Windows working tree.
            filter<FixCrLfFilter>("eol" to FixCrLfFilter.CrLf.newInstance(shellScriptLineEnding))
            into("scripts")
        }
        into(dockerContext)
    }
    tasks.register<Exec>("build${prefix}DockerImage") {
        group = "verification"
        description = "Builds the $tag service test image using the root Dockerfile."
        dependsOn(copied)
        workingDir(dockerContext)
        commandLine(
            "docker", "build", "--file", "Dockerfile", "--target", serviceName,
            "--build-arg", "BUILDER_STAGE=prebuilt-builder", "--tag", "a-$imageName:$tag", ".",
        )
        outputs.upToDateWhen { false }
    }
}

if (coverageEnabled.get()) {
    // Ordinary host fixtures still use latest; E2E fixtures use the separately instrumented image.
    tasks.named("buildTestDockerImage") { dependsOn("buildE2eCoverageDockerImage") }
}
