/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.core.service.error

import com.storyteller_f.a.backend.core.error.DatabaseError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

internal class ScenarioErrorTest {
    @Test
    fun `unexpected errors are not reclassified by cause`() {
        val cause = IllegalArgumentException("provider failed")
        val mapped = TopicError.fromError(RoomError.Unexpected(cause))
        assertSame(cause, assertIs<TopicError.Unexpected>(mapped).cause)
    }

    @Test
    fun `constraint classification survives scene changes`() {
        val cause = IllegalStateException("constraint violated")
        val database = DatabaseError.ConstraintViolation(cause)
        val room = assertIs<RoomError.Conflict>(RoomError.fromError(database))
        val community = assertIs<CommunityError.Conflict>(CommunityError.fromError(room))
        assertSame(cause, community.cause)
    }

    @Test
    fun `permission category survives scene changes`() {
        val cause = IllegalStateException("permission denied")
        val room = RoomError.Forbidden(cause)
        val topic = assertIs<TopicError.Forbidden>(TopicError.fromError(room))
        assertSame(cause, topic.cause)
    }
}
