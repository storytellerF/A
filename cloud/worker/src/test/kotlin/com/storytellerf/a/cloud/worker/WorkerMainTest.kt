/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker

import com.storyteller_f.a.cloud.worker.TASK_CONFIG_POLL_MILLIS
import com.storyteller_f.a.cloud.worker.TopicSafetyReviewerProvider
import com.storyteller_f.a.cloud.worker.executeConfiguredTaskIteration
import com.storyteller_f.shared.model.TaskConfig
import com.storyteller_f.shared.model.TaskRecordType
import com.storytellerf.a.cloud.worker.moderation.TopicSafetyReviewer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

internal class WorkerMainTest {
    @Test
    fun `reviewer provider retries initialization`() {
        runTest {
            var attempts = 0
            val provider =
                TopicSafetyReviewerProvider {
                    attempts += 1
                    if (attempts == 1) null else TopicSafetyReviewer { false }
                }

            assertEquals(null, provider.get())
            assertNotNull(provider.get())
            assertNotNull(provider.get())
            assertEquals(2, attempts)
        }
    }

    @Test
    fun `missing task configuration does not execute task`() {
        runTest {
            var isExecuted = false
            var waited = 0L

            executeConfiguredTaskIteration(
                name = "intro",
                configResult = Result.success(null),
                task = { isExecuted = true },
                wait = { waited = it },
            )

            assertFalse(isExecuted)
            assertEquals(TASK_CONFIG_POLL_MILLIS, waited)
        }
    }

    @Test
    fun `disabled task configuration does not execute task`() {
        runTest {
            val config =
                TaskConfig(
                    type = TaskRecordType.INTRO,
                    isEnabled = false,
                    fetchSize = 10,
                    waitDurationMillis = 50_000,
                )
            var isExecuted = false
            var waited = 0L

            executeConfiguredTaskIteration(
                name = "intro",
                configResult = Result.success(config),
                task = { isExecuted = true },
                wait = { waited = it },
            )

            assertFalse(isExecuted)
            assertEquals(TASK_CONFIG_POLL_MILLIS, waited)
        }
    }

    @Test
    fun `enabled task uses configured limits`() {
        runTest {
            val config =
                TaskConfig(
                    type = TaskRecordType.INTRO,
                    isEnabled = true,
                    fetchSize = 23,
                    waitDurationMillis = 4_000,
                )
            var executedConfig: TaskConfig? = null
            var waited = 0L

            executeConfiguredTaskIteration(
                name = "intro",
                configResult = Result.success(config),
                task = { executedConfig = it },
                wait = { waited = it },
            )

            assertEquals(config, executedConfig)
            assertEquals(config.waitDurationMillis, waited)
        }
    }
}
