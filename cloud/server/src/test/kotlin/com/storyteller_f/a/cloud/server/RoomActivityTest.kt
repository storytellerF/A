/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.api.NewRoom
import com.storyteller_f.a.api.NewTitle
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.api.RoomActivityInfo
import com.storyteller_f.a.api.RoomActivityListResponse
import com.storyteller_f.a.backend.exposed.database.ExposedTopicDatabase
import com.storyteller_f.a.backend.exposed.tables.Topics
import com.storyteller_f.a.client.core.ServerErrorException
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.addReadLog
import com.storyteller_f.a.client.core.createRoom
import com.storyteller_f.a.client.core.createTitle
import com.storyteller_f.a.client.core.createTopic
import com.storyteller_f.a.client.core.exitCommunity
import com.storyteller_f.a.client.core.exitRoom
import com.storyteller_f.a.client.core.getJoinedPinnedTopics
import com.storyteller_f.a.client.core.getRoomActivities
import com.storyteller_f.a.client.core.getRoomMembersPublicKeys
import com.storyteller_f.a.client.core.getRoomTopics
import com.storyteller_f.a.client.core.getUnreadRoomActivities
import com.storyteller_f.a.client.core.hasUnreadRooms
import com.storyteller_f.a.client.core.joinRoom
import com.storyteller_f.a.client.core.pinTopic
import com.storyteller_f.a.client.core.sendMessage
import com.storyteller_f.a.client.core.unpinTopic
import com.storyteller_f.a.cloud.core.service.createTopicAtRoom
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.NewRoomTopic
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.type.ObjectStatus
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.UserStatus
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomActivityTest {
    @Test
    fun `batch counts direct messages and preserves order`() =
        test {
        attachSession { account ->
            val community = createCommunityForTest()
            val room = createPublicRoomForTest(community.id, "active", "Active")
            val empty = createPublicRoomForTest(community.id, "empty", "Empty")
            val first = createRoomMessage(account.uid, room.id, "First")
            val second = createRoomMessage(account.uid, room.id, "Second")
            createRoomMessage(account.uid, second.id, "Reply excluded", ObjectType.TOPIC)
            val initial = getRoomActivities(listOf(empty.id, room.id, room.id)).getOrThrow().data
            val latest = assertNotNull(initial.last().latestMessage)
            assertEquals(listOf(empty.id, room.id), initial.map { it.room.id })
            assertNull(initial.first().latestMessage)
            assertEquals(0L, initial.first().unreadMessageCount)
            assertEquals(2L, initial.last().unreadMessageCount)
            assertEquals(second.id, initial.last().latestMessageId)
            assertEquals(
                getRoomTopics(
                    room.id,
                    paginationQuery = PaginationQuery(),
                ).getOrThrow().data.first { it.id == second.id }.createdTime,
                latest.createdTime,
            )
            assertEquals(second.author, latest.extension?.authorInfo?.id)
            assertEquals("Second", assertIs<TopicContent.Plain>(latest.content).plain)
            workerBackend.database.room.updateRoomStatus(room.id, ObjectStatus.READ_ONLY).getOrThrow()
            assertEquals(
                ObjectStatus.READ_ONLY,
                getOnlyRoomActivity(room.id).room.status,
            )
            addReadLog(UpdateUserRead(room.tuple(), first.id)).getOrThrow()
            assertEquals(1L, getOnlyRoomActivity(room.id).unreadMessageCount)
            addReadLog(UpdateUserRead(room.tuple(), second.id)).getOrThrow()
            assertEquals(0L, getOnlyRoomActivity(room.id).unreadMessageCount)
            assertEquals(0, hasUnreadRooms().getOrThrow().unreadCount)
            addReadLog(UpdateUserRead(room.tuple(), first.id)).getOrThrow()
            assertEquals(second.id, getOnlyRoomActivity(room.id).room.lastRead)
            assertEquals(0L, getOnlyRoomActivity(room.id).unreadMessageCount)
            assertEquals(0, hasUnreadRooms().getOrThrow().unreadCount)
            exitRoom(room.id).getOrThrow()
            assertTrue(getRoomActivities(listOf(room.id)).getOrThrow().data.isEmpty())
        }
    }

    @Test
    fun `unread pages contain each room once`() =
        test {
        attachSession { account ->
            val community = createCommunityForTest()
            val rooms = (1..3).map { createPublicRoomForTest(community.id, "r$it", "Room $it") }
            repeat(3) { index -> rooms.forEach { createRoomMessage(account.uid, it.id, "Message $index") } }
            val first = getUnreadRoomActivities(PaginationQuery(size = 1)).getOrThrow()
            val second =
                getUnreadRoomActivities(
                    PaginationQuery(first.pagination?.nextPageToken, size = 1),
                ).getOrThrow()
            val third =
                getUnreadRoomActivities(
                    PaginationQuery(second.pagination?.nextPageToken, size = 1),
                ).getOrThrow()
            val last = getUnreadRoomActivities(PaginationQuery(third.pagination?.nextPageToken, size = 1)).getOrThrow()
            val pages = listOf(first, second, third)
            assertEquals(rooms.reversed().map { it.id }, pages.map { it.data.single().room.id })
            pages.forEach {
                assertEquals(3L, it.pagination?.total)
                assertEquals(3L, it.data.single().unreadMessageCount)
            }
            assertTrue(last.data.isEmpty())
            val previous =
                getUnreadRoomActivities(
                    PaginationQuery(prePageToken = third.pagination?.prePageToken, size = 1),
                )
                    .getOrThrow()
            assertEquals(second.data.single().room.id, previous.data.single().room.id)
            val previousTwo =
                getUnreadRoomActivities(
                    PaginationQuery(prePageToken = third.pagination?.prePageToken, size = 2),
                ).getOrThrow()
            assertEquals(
                listOf(first.data.single().room.id, second.data.single().room.id),
                previousTwo.data.map { it.room.id },
            )
            val read = first.data.single()
            addReadLog(UpdateUserRead(read.room.tuple(), requireNotNull(read.latestMessageId))).getOrThrow()
            assertEquals(2L, getUnreadRoomActivities(PaginationQuery()).getOrThrow().pagination?.total)
            assertEquals(2, hasUnreadRooms().getOrThrow().unreadCount)
        }
    }

    @Test
    fun `private activity requires membership and keys`() =
        test {
        val frames = mutableListOf<RoomFrame>()
        val owner =
            attachSession(onReceive = { frame, _, _ -> frames.add(frame) }) {
                val room = createRoom(NewRoom("private", "Private")).getOrThrow()
                val keys = getRoomMembersPublicKeys(room.id, PaginationQuery()).getOrThrow().data
                createTopicInRoomAndWait(frames) { sendMessage(room.tuple(), true, "Private message", keys) }
                val wire =
                    with(
                        client,
                    ) {
                        get(
                            "/users/room-activities",
                        ) { parameter("roomIds", room.id.toString()) }.body<RoomActivityListResponse>()
                    }
                assertIs<TopicContent.Encrypted>(wire.data.single().latestMessage?.content)
                assertEquals(
                    "Private message",
                    assertIs<TopicContent.Plain>(
                        getOnlyRoomActivity(room.id).latestMessage?.content,
                    ).plain,
                )
                room.id
            }
        val lateMember =
            attachSession {
                assertTrue(getRoomActivities(listOf(owner.custom)).getOrThrow().data.isEmpty())
            }
        loginSession(owner) {
            createTitle(
                NewTitle("join", TitleType.JOIN, lateMember.uid, owner.custom, ObjectType.ROOM, ""),
            ).getOrThrow()
        }
        loginSession(lateMember) {
            joinRoom(owner.custom).getOrThrow()
            val activity = getOnlyRoomActivity(owner.custom)
            assertEquals(1L, activity.unreadMessageCount)
            assertIs<TopicContent.DecryptFailed>(activity.latestMessage?.content)
            exitRoom(owner.custom).getOrThrow()
            assertTrue(getUnreadRoomActivities(PaginationQuery()).getOrThrow().data.isEmpty())
        }
        noneSession {
            val error =
                assertFailsWith<ServerErrorException> {
                    client.get("/users/room-activities") { parameter("roomIds", owner.custom.toString()) }
                        .body<RoomActivityListResponse>()
                }
            assertEquals(HttpStatusCode.Unauthorized, error.status)
        }
    }

    @Test
    fun `pins follow joined scope and unpin or exit`() =
        test {
        val source =
            attachSession {
                val community = createCommunityForTest()
                val old = createTopic(ObjectType.COMMUNITY, community.id, "Old pin").getOrThrow()
                val recent = createTopic(ObjectType.COMMUNITY, community.id, "Recent pin").getOrThrow()
                pinTopic(old.id).getOrThrow()
                pinTopic(recent.id).getOrThrow()
                val page = getJoinedPinnedTopics(PaginationQuery(size = 1)).getOrThrow()
                assertEquals(recent.id, page.data.single().id)
                assertEquals(
                    old.id,
                    getJoinedPinnedTopics(PaginationQuery(page.pagination?.nextPageToken, size = 1))
                        .getOrThrow().data.single().id,
                )
                unpinTopic(recent.id).getOrThrow()
                assertEquals(listOf(old.id), getJoinedPinnedTopics(PaginationQuery()).getOrThrow().data.map { it.id })
                community.id
            }
        attachSession { assertTrue(getJoinedPinnedTopics(PaginationQuery()).getOrThrow().data.isEmpty()) }
        loginSession(source) {
            exitCommunity(source.custom).getOrThrow()
            assertTrue(getJoinedPinnedTopics(PaginationQuery()).getOrThrow().data.isEmpty())
        }
    }

    @Test
    fun `read only accounts retain context read access`() =
        test {
        attachSession { account ->
            val community = createCommunityForTest()
            val room = createPublicRoomForTest(community.id, "readonly", "Read only")
            createRoomMessage(account.uid, room.id, "Message")
            val topic = createTopic(ObjectType.COMMUNITY, community.id, "Pinned").getOrThrow()
            pinTopic(topic.id).getOrThrow()
            workerBackend.database.user.updateUserStatus(account.uid, UserStatus.READ_ONLY).getOrThrow()
            assertEquals(1L, getOnlyRoomActivity(room.id).unreadMessageCount)
            val unread = getUnreadRoomActivities(PaginationQuery()).getOrThrow().data.single()
            assertEquals(room.id, unread.room.id)
            assertEquals(topic.id, getJoinedPinnedTopics(PaginationQuery()).getOrThrow().data.single().id)
        }
    }

    @Test
    fun `deleted messages disappear from activity and count`() =
        test {
        attachSession { account ->
            val community = createCommunityForTest()
            val room = createPublicRoomForTest(community.id, "deletion", "Deletion")
            val first = createRoomMessage(account.uid, room.id, "First")
            val second = createRoomMessage(account.uid, room.id, "Second")
            val database = workerBackend.database.topic as ExposedTopicDatabase
            database.databaseSession.dbQuery { Topics.deleteWhere { Topics.id eq second.id } }.getOrThrow()
            val remaining = getOnlyRoomActivity(room.id)
            assertEquals(first.id, remaining.latestMessageId)
            assertEquals(1L, remaining.unreadMessageCount)
            database.databaseSession.dbQuery { Topics.deleteWhere { Topics.id eq first.id } }.getOrThrow()
            val empty = getOnlyRoomActivity(room.id)
            assertNull(empty.latestMessage)
            assertNull(empty.latestMessageId)
            assertEquals(0L, empty.unreadMessageCount)
            assertTrue(getUnreadRoomActivities(PaginationQuery()).getOrThrow().data.isEmpty())
        }
    }

    @Test
    fun `activity rejects invalid sizes and identifiers`() =
        test {
        attachSession {
            assertTrue(getUnreadRoomActivities(PaginationQuery(size = 101)).isFailure)
            assertTrue(getJoinedPinnedTopics(PaginationQuery(size = 0)).isFailure)
            listOf("", "-1", "invalid", List(101) { "1" }.joinToString(",")).forEach { ids ->
                val error =
                    assertFailsWith<ServerErrorException> {
                        client.get("/users/room-activities") {
                            parameter("roomIds", ids)
                        }.body<RoomActivityListResponse>()
                    }
                assertEquals(HttpStatusCode.BadRequest, error.status)
            }
            assertFalse(hasUnreadRooms().getOrThrow().hasUnread)
        }
    }
}

private suspend fun TestMate.createRoomMessage(
    uid: PrimaryKey,
    parentId: PrimaryKey,
    text: String,
    parentType: ObjectType = ObjectType.ROOM,
): TopicInfo =
    requireNotNull(
    workerBackend.createTopicAtRoom(NewRoomTopic(parentType, parentId, TopicContent.Plain(text)), uid).getOrThrow(),
)

private suspend fun UserSessionManager.getOnlyRoomActivity(roomId: PrimaryKey): RoomActivityInfo =
    getRoomActivities(listOf(roomId)).getOrThrow().data.single()
