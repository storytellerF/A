/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker.moderation

import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.model.TaskFailureType
import com.storyteller_f.shared.utils.now
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TopicModerationTaskTest {
    @Test
    fun `user and community topics are reviewable`() {
        assertTrue(buildTopic(ObjectType.USER).isReviewable(emptySet()))
        assertTrue(buildTopic(ObjectType.COMMUNITY).isReviewable(emptySet()))
    }

    @Test
    fun `only topics in public rooms are reviewable`() {
        val topic = buildTopic(ObjectType.ROOM)

        assertTrue(topic.isReviewable(setOf(ROOT_ID)))
        assertFalse(topic.isReviewable(emptySet()))
    }

    @Test
    fun `encrypted and unsupported topics are skipped`() {
        assertFalse(buildTopic(ObjectType.ROOM, isEncrypted = true).isReviewable(setOf(ROOT_ID)))
        assertFalse(buildTopic(ObjectType.TOPIC).isReviewable(emptySet()))
    }

    @Test
    fun `moderation response accepts only exact decisions`() {
        assertFalse(parseSafetyDecision(" SAFE\n"))
        assertTrue(parseSafetyDecision("unsafe"))
        assertFailsWith<IllegalStateException> {
            parseSafetyDecision("UNSAFE because the topic is violent")
        }
    }

    @Test
    fun `invalid moderation response has model response failure type`() {
        val failure =
            assertFailsWith<UnexpectedTopicSafetyDecisionException> {
                parseSafetyDecision("UNSAFE because the topic is violent")
            }

        assertTrue(failure.toTaskFailureType() == TaskFailureType.MODEL_RESPONSE)
    }

    private fun buildTopic(rootType: ObjectType, isEncrypted: Boolean = false): Topic {
        val topic =
            Topic(
                id = 1,
                createdTime = now(),
                author = 2,
                parentId = ROOT_ID,
                parentType = rootType,
                rootId = ROOT_ID,
                rootType = rootType,
                content = "content".encodeToByteArray(),
                isEncrypted = isEncrypted,
                level = 1,
            )
        return topic
    }

    private companion object {
        const val ROOT_ID = 3L
    }
}
