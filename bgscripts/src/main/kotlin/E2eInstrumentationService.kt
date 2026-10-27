/*
 * This is a private project. All rights reserved.
 */

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/** Kover's batch instrumentation temporarily changes its process-global ConDy policy. */
abstract class E2eInstrumentationService : BuildService<BuildServiceParameters.None>
