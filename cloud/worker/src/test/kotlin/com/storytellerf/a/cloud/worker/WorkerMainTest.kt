/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.cloud.worker.TASK_CONFIG_POLL_MILLIS
import com.storyteller_f.a.cloud.worker.createTopicSafetyReviewer
import com.storyteller_f.a.cloud.worker.executeConfiguredTaskIteration
import com.storyteller_f.a.cloud.worker.isTopicModerationEnabled
import com.storyteller_f.shared.model.TaskConfig
import com.storyteller_f.shared.model.TaskRecordType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class WorkerMainTest {
    @Test
    fun `topic moderation defaults to enabled`() {
        assertTrue(isTopicModerationEnabled(MergedEnv(emptyList())))
    }

    @Test
    fun `topic moderation can be disabled`() {
        val env = MergedEnv(listOf(mapOf("TOPIC_MODERATION_ENABLED" to " FaLsE ")))

        assertFalse(isTopicModerationEnabled(env))
    }

    @Test
    fun `invalid topic moderation setting is rejected`() {
        val env = MergedEnv(listOf(mapOf("TOPIC_MODERATION_ENABLED" to "invalid")))

        assertFailsWith<IllegalArgumentException> {
            isTopicModerationEnabled(env)
        }
    }

    @Test
    fun `disabled topic moderation skips model setup`() {
        val env = MergedEnv(listOf(mapOf("TOPIC_MODERATION_ENABLED" to "false")))

        val reviewer =
            createTopicSafetyReviewer(
                env = env,
                modelProvider = { error("model setup must be skipped") },
                reviewerFactory = { error("reviewer setup must be skipped") },
            )

        assertNull(reviewer)
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
