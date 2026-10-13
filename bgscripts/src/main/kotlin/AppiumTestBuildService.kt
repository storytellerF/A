/*
 * This is a private project. All rights reserved.
 */

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/** Serializes Appium tasks sharing a platform within one Gradle build. */
abstract class AppiumTestBuildService : BuildService<BuildServiceParameters.None>
