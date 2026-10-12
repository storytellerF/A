/*
 * This is a private project. All rights reserved.
 */

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/** Let Gradle serialize memory-intensive image builds across service projects. */
abstract class DockerImageBuildService : BuildService<BuildServiceParameters.None>
