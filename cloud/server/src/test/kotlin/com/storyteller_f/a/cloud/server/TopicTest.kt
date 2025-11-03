package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.api.core.NewCommunity
import com.storyteller_f.a.api.core.NewRoom
import com.storyteller_f.a.api.core.NewTitle
import com.storyteller_f.a.api.core.PaginationQuery
import com.storyteller_f.a.client.core.UploadData
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.addReaction
import com.storyteller_f.a.client.core.addReadLog
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createRoom
import com.storyteller_f.a.client.core.createTitle
import com.storyteller_f.a.client.core.createTopic
import com.storyteller_f.a.client.core.deleteReaction
import com.storyteller_f.a.client.core.getCommunityInfo
import com.storyteller_f.a.client.core.getCommunityTopics
import com.storyteller_f.a.client.core.getReactions
import com.storyteller_f.a.client.core.getRecommendTopics
import com.storyteller_f.a.client.core.getRoomInfo
import com.storyteller_f.a.client.core.getRoomMembersPublicKeys
import com.storyteller_f.a.client.core.getRoomTopics
import com.storyteller_f.a.client.core.getTopicInfo
import com.storyteller_f.a.client.core.getTopicSnapshot
import com.storyteller_f.a.client.core.getUserTopics
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.a.client.core.joinRoom
import com.storyteller_f.a.client.core.pinTopic
import com.storyteller_f.a.client.core.searchTopics
import com.storyteller_f.a.client.core.sendMessage
import com.storyteller_f.a.client.core.unpinTopic
import com.storyteller_f.a.client.core.upload
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.type.ObjectType
import io.ktor.client.call.body
import io.ktor.client.plugins.onDownload
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.defaultForFileExtension
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.writeString
import org.junit.jupiter.api.assertNotNull
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Suppress("LongMethod")
class TopicTest {

    @Test
    fun `test community topic search and pagination`() = test {
        attachSession {
            val communityId = createCommunity(NewCommunity("aid", "name")).getOrThrow().id
            val lastTopic =
                createTopic(ObjectType.COMMUNITY, communityId, "hello world").getOrThrow()
            createTopic(ObjectType.COMMUNITY, communityId, "sysroot").getOrThrow()
            val firstTopic =
                createTopic(ObjectType.COMMUNITY, communityId, "best world").getOrThrow()
            val topics = searchTopics(1, listOf("world")).getOrThrow()
            assertEquals(2, topics.pagination?.total)
            assertEquals(1, topics.data.size)
            assertEquals(firstTopic.id, topics.data.first().id)
            val topics2 = searchTopics(
                1,
                listOf("world"),
                nextTopicId = topics.data.first().id.toString()
            ).getOrThrow()
            assertEquals(lastTopic.id, topics2.data.first().id)
        }
    }

    @Test
    fun `test community topic author is not null`() = test {
        attachSession {
            val communityId = createCommunity(NewCommunity("aid", "name")).getOrThrow().id
            createTopic(ObjectType.COMMUNITY, communityId, "hello world").getOrThrow()
            createTopic(ObjectType.COMMUNITY, communityId, "best world").getOrThrow()
            searchTopics(10, listOf("world")).getOrThrow().data.forEach {
                assertNotNull(it.extension?.authorInfo)
            }
            getCommunityTopics(
                communityId,
                paginationQuery = PaginationQuery(size = 10)
            ).getOrThrow().data.forEach {
                assertNotNull(it.extension?.authorInfo)
            }
        }
    }

    @Test
    fun `test community topic has comment and comment count`() = test {
        attachSession {
            val communityId = createCommunity(NewCommunity("aid", "name")).getOrThrow().id
            val topicId =
                createTopic(ObjectType.COMMUNITY, communityId, "hello world").getOrThrow().id
            createTopic(ObjectType.TOPIC, topicId, "best world").getOrThrow()
            searchTopics(10, listOf("world")).getOrThrow().data.forEach {
                assertNotNull(it.hasComment)
                assertEquals(1, it.commentCount)
            }
            getCommunityTopics(
                communityId,
                paginationQuery = PaginationQuery(size = 10)
            ).getOrThrow().data.forEach {
                assertNotNull(it.hasComment)
                assertEquals(1, it.commentCount)
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `test topic snapshot`() = test {
        attachSession {
            val bytes =
                ClassLoader.getSystemResourceAsStream("avatar1.png")!!.buffered().readBytes()
            val tmpFile = File("build/test/tmp/avatar1.png")
            tmpFile.parentFile!!.mkdirs()
            tmpFile.writeBytes(bytes)
            val size = tmpFile.length()
            val info = upload(
                ObjectTuple(it.uid, ObjectType.USER),
                getUploadDataFromStream(size, tmpFile)
            ).getOrThrow().data.first()
            val communityId = createCommunity(NewCommunity("name", "aid")).getOrThrow().id
            val topicInfo = createTopic(
                ObjectType.COMMUNITY,
                communityId,
                """hello
                |
                |![png](${info.name})
                """.trimMargin()
            ).getOrThrow()
            val fileInfo = getTopicSnapshot(topicInfo.id).getOrThrow()
            val url = fileInfo.url
            val file = File("build/test/tmp/${Uuid.random()}.pdf")
            val httpResponse = client.get(url) {
                onDownload { bytesSentTotal, contentLength ->
                    println("Received $bytesSentTotal bytes from $contentLength")
                }
            }
            val responseBody: ByteArray = httpResponse.body()
            file.writeBytes(responseBody)
        }
    }

    @Test
    fun `test add reaction`() = test {
        val emoji = "\uD83D\uDE00"
        attachSession {
            val c = createCommunity(NewCommunity("name", "aid")).getOrThrow()
            val topicInfo = createTopic(ObjectType.COMMUNITY, c.id, "hello").getOrThrow()
            // 测试并发
            repeat(4) {
                val reactionInfo = addReaction(topicInfo.id, emoji).getOrThrow()
                assertEquals(emoji, reactionInfo.emoji)
                assertTrue(reactionInfo.hasReacted)
            }
            // 测试幂等
            addReaction(topicInfo.id, emoji).getOrThrow()
            topicInfo
        }
    }

    @Test
    fun `test delete reaction`() = test {
        val emoji = "\uD83D\uDE00"
        attachSession {
            val c = createCommunity(NewCommunity("name", "aid")).getOrThrow()
            val topicInfo = createTopic(ObjectType.COMMUNITY, c.id, "hello").getOrThrow()
            // 测试幂等
            val old = addReaction(topicInfo.id, emoji).getOrThrow()
            assertEquals(1, old.count)
            assertTrue(old.hasReacted)
            assertListSize(1, getReactions(topicInfo.id, 10))
            val reactionInfo = deleteReaction(emoji, topicInfo.id).getOrThrow()
            assertEquals(0, reactionInfo.count)
            assertFalse(reactionInfo.hasReacted)
            assertListSize(0, getReactions(topicInfo.id, 10))
        }
    }

    @Test
    fun `test reaction permission check`() = test {
        val emoji = "\uD83D\uDE00"
        val session = attachSession {
            val communityInfo = createCommunity(NewCommunity("name", "aid")).getOrThrow()
            val topicInfo =
                createTopic(ObjectType.COMMUNITY, communityInfo.id, "hello").getOrThrow()
            addReaction(topicInfo.id, emoji).getOrThrow()
            topicInfo
        }
        val topicId = session.custom.id
        attachSession {
            assertFails {
                addReaction(topicId, emoji).getOrThrow()
            }
            joinCommunity(session.custom.rootId)
            addReaction(topicId, emoji).getOrThrow()
        }
    }

    @Test
    fun `test file in user topic`() = test {
        attachSession {
            val string = "hello"
            val fileInfo = upload(
                ObjectTuple(it.uid, ObjectType.USER),
                getUploadDataFromText(string)
            ).getOrThrow().data.first()
            val info =
                createTopic(
                    ObjectType.USER,
                    it.uid,
                    "![hello.txt](${fileInfo.name})"
                ).getOrThrow()
            val plain = info.content as TopicContent.Plain
            assertEquals(fileInfo.fullName, plain.fileInfos.first().fullName)
            val topicInfo = getTopicInfo(info.id).getOrThrow()
            assertEquals(
                fileInfo.fullName,
                (topicInfo.content as TopicContent.Plain).fileInfos.first().fullName
            )
        }
    }

    @Test
    fun `test create user topic`() = test {
        attachSession {
            createTopic(ObjectType.USER, it.uid, "hello").getOrThrow()
            // 查询单个topic
            assertListSize(
                1,
                getUserTopics(
                    it.uid,
                    paginationQuery = PaginationQuery(null, null, size = 10)
                )
            )
            createTopic(ObjectType.USER, it.uid, "test").getOrThrow()
            // 查询多个topic
            assertListSize(
                2,
                getUserTopics(
                    it.uid,
                    paginationQuery = PaginationQuery(null, null, size = 10)
                )
            )
        }
    }

    @Test
    fun `test forbid use api send message in room`() = test {
        val (communityId, publicRoomId) = attachSession {
            val communityId = createCommunity(NewCommunity("test1", "test1")).getOrThrow().id
            val publicRoomId =
                createRoom(NewRoom("room1", "room1", communityId = communityId)).getOrThrow().id
            communityId to publicRoomId
        }.custom
        attachSession {
            joinCommunity(communityId).getOrThrow()
            joinRoom(publicRoomId).getOrThrow()
            assertFails {
                createTopic(
                    ObjectType.ROOM,
                    publicRoomId,
                    "forbid use api add topic to room"
                ).getOrThrow()
            }
        }
    }

    @Test
    fun `test community room permission check`() = test {
        val (communityId, publicRoomId) = attachSession {
            val communityId = createCommunity(NewCommunity("test1", "test1")).getOrThrow().id
            val publicRoomId =
                createRoom(NewRoom("room1", "room1", communityId = communityId)).getOrThrow().id
            communityId to publicRoomId
        }.custom
        val receivedFrame = mutableListOf<RoomFrame>()
        attachSession({ roomFrame, model, session ->
            receivedFrame.add(roomFrame)
        }) {
            val roomInfo = getRoomInfo(publicRoomId).getOrThrow()
            createTopicInRoomAndWait(receivedFrame) {
                sendMessage(
                    ObjectTuple(roomInfo.id, ObjectType.ROOM),
                    roomInfo.isPrivate,
                    "test",
                    emptyList()
                )
            }
            assertTrue(receivedFrame.first() is RoomFrame.Error)
            joinCommunity(communityId).getOrThrow()
            joinRoom(publicRoomId).getOrThrow()
            createTopicInRoomAndWait(receivedFrame) {
                sendMessage(
                    ObjectTuple(roomInfo.id, ObjectType.ROOM),
                    roomInfo.isPrivate,
                    "test",
                    emptyList()
                )
            }
            assertListSize(
                1,
                getRoomTopics(publicRoomId, paginationQuery = PaginationQuery(size = 10))
            )
        }
        receivedFrame.clear()
    }

    private suspend fun UserSessionManager.createTopicInRoomAndWait(
        receivedFrame: MutableList<RoomFrame>,
        block: suspend DefaultClientWebSocketSession.() -> Unit
    ) {
        val old = receivedFrame.size
        webSocketClient.useWebSocket(block)?.join()
        while (true) {
            if (receivedFrame.size == old + 1) {
                break
            }
            withContext(Dispatchers.IO) {
                delay(100)
            }
        }
    }

    @Test
    fun `test create topic in private room`() = test {
        val user2 = attachSession {
        }
        val user1 = attachSession {
            val id = createRoom(NewRoom("room2", "room2")).getOrThrow().id
            createTitle(
                NewTitle("join", TitleType.JOIN, user2.uid, id, ObjectType.ROOM, "")
            )
            id
        }
        val privateRoomId = user1.custom
        val receivedFrame = mutableListOf<RoomFrame>()
        loginSession(user2, { roomFrame, model, session ->
            receivedFrame.add(roomFrame)
        }) {
            joinRoom(privateRoomId).getOrThrow()
            val roomInfo2 = getRoomInfo(privateRoomId).getOrThrow()
            val keys = getRoomMembersPublicKeys(
                privateRoomId,
                PaginationQuery(null, size = 10)
            ).getOrThrow().data
            createTopicInRoomAndWait(receivedFrame) {
                sendMessage(
                    ObjectTuple(roomInfo2.id, ObjectType.ROOM),
                    roomInfo2.isPrivate,
                    "hello",
                    keys
                )
            }
            assertNotNull((receivedFrame.first() as RoomFrame.NewTopicInfo).topicInfo.extension?.authorInfo)
        }
        receivedFrame.clear()
    }

    @Test
    fun `test private room join`() = test {
        val user1 = attachSession {
            val id = createRoom(NewRoom("room2", "room2")).getOrThrow().id
            id
        }
        val privateRoomId = user1.custom

        val user2 = attachSession {
            assertFails {
                joinRoom(privateRoomId).getOrThrow()
            }
        }
        loginSession(user1) {
            createTitle(
                NewTitle("join", TitleType.JOIN, user2.uid, privateRoomId, ObjectType.ROOM, "")
            )
        }
        loginSession(user2, { roomFrame, model, session ->
        }) {
            joinRoom(privateRoomId).getOrThrow()
        }
    }

    @Test
    fun `test create in user`() = test {
        attachSession {
            createTopic(ObjectType.USER, it.uid, "hello").getOrThrow()
        }
    }

    @Test
    fun `test recommend`() = test {
        attachSession {
            val id = createCommunity(NewCommunity("c1", "c1")).getOrThrow().id
            repeat(4) {
                createTopic(ObjectType.COMMUNITY, id, "hello $it").getOrThrow()
            }
            id
        }.custom
        val custom2 = attachSession {
            val id = createCommunity(NewCommunity("c2", "c2")).getOrThrow().id
            createTopic(ObjectType.COMMUNITY, id, "hello 2").getOrThrow()
            id
        }.custom
        attachSession {
            joinCommunity(custom2).getOrThrow()
            createTopic(ObjectType.COMMUNITY, custom2, "only").getOrThrow()
            assertListSize(1, getRecommendTopics(PaginationQuery(null, size = 10)))
        }
    }

    @Test
    fun `test none session recommend`() = test {
        attachSession {
            val id = createCommunity(NewCommunity("c1", "c1")).getOrThrow().id
            repeat(4) {
                createTopic(ObjectType.COMMUNITY, id, "hello $it").getOrThrow()
            }
        }
        attachSession {
            val id = createCommunity(NewCommunity("c2", "c2")).getOrThrow().id
            createTopic(ObjectType.COMMUNITY, id, "hello 2").getOrThrow()
            id
        }
        noneSession {
            assertEquals(
                5,
                getRecommendTopics(PaginationQuery(null, size = 10)).getOrThrow().data.size
            )
        }
    }

    @Test
    fun `test room last read`() {
        val receivedFrame = mutableListOf<RoomFrame>()
        test {
            attachSession({ roomFrame, model, session ->
                receivedFrame.add(roomFrame)
            }) {
                val roomInfo = createRoom(NewRoom("r1", "r1")).getOrThrow()
                val keys =
                    getRoomMembersPublicKeys(
                        roomInfo.id,
                        PaginationQuery(null, size = 10)
                    ).getOrThrow().data
                createTopicInRoomAndWait(receivedFrame) {
                    sendMessage(roomInfo.tuple(), true, "hello", keys)
                }
                val topicId = (receivedFrame.first() as RoomFrame.NewTopicInfo).topicInfo.id
                addReadLog(
                    UpdateUserRead(
                        roomInfo.tuple(),
                        topicId
                    )
                ).getOrThrow()
                assertEquals(topicId, getRoomInfo(roomInfo.id).getOrThrow().lastRead)
                receivedFrame.clear()
            }
        }
    }

    @Test
    fun `test community last read`() = test {
        attachSession {
            val communityInfo = createCommunity(NewCommunity("r1", "r1")).getOrThrow()
            val topic =
                createTopic(ObjectType.COMMUNITY, communityInfo.id, "hello").getOrThrow()
            addReadLog(
                UpdateUserRead(
                    communityInfo.tuple(),
                    topic.id
                )
            ).getOrThrow()
            assertEquals(topic.id, getCommunityInfo(communityInfo.id).getOrThrow().lastRead)
            val subTopic = createTopic(ObjectType.TOPIC, topic.id, "world").getOrThrow()
            addReadLog(UpdateUserRead(topic.tuple(), subTopic.id)).getOrThrow()
            assertEquals(subTopic.id, getTopicInfo(topic.id).getOrThrow().lastRead)
        }
    }

    @Test
    fun `test pin topic`() = test {
        attachSession {
            val communityInfo = createCommunity(NewCommunity("r1", "r1")).getOrThrow()
            val topic =
                createTopic(ObjectType.COMMUNITY, communityInfo.id, "hello").getOrThrow()
            val pinned = pinTopic(topic.id).getOrThrow()
            assertTrue(pinned.isPin)
            val unpinned = unpinTopic(topic.id).getOrThrow()
            assertFalse(unpinned.isPin)
            val userTopic = createTopic(ObjectType.USER, it.uid, "hello").getOrThrow()
            val userPinned = pinTopic(userTopic.id).getOrThrow()
            assertTrue(userPinned.isPin)
            val userUnpinned = unpinTopic(userTopic.id).getOrThrow()
            assertFalse(userUnpinned.isPin)
        }
    }

    @Test
    fun `test topic level`() = test {
        attachSession {
            val topicInfo = createTopic(ObjectType.USER, it.uid, "hello").getOrThrow()
            assertEquals(1, topicInfo.level)
        }
    }

    @Test
    fun `test community topic latest topic`() = test {
        attachSession {
            val communityInfo = createCommunity(NewCommunity("r1", "r1")).getOrThrow()
            val topic =
                createTopic(ObjectType.COMMUNITY, communityInfo.id, "hello").getOrThrow()
            val info = getCommunityInfo(communityInfo.id).getOrThrow()
            assertEquals(topic.id, info.latestTopic)
        }
    }
}

private fun getUploadDataFromStream(
    size: Long,
    tmpFile: File
) = UploadData(
    size,
    "avatar1.png",
    ContentType.defaultForFileExtension("png")
) {
    tmpFile.inputStream().asInput()
}

fun getUploadDataFromText(string: String) = UploadData(
    string.length.toLong(),
    "hello.txt",
    ContentType.defaultForFileExtension("txt")
) {
    Buffer().apply {
        writeString(string)
    }
}
