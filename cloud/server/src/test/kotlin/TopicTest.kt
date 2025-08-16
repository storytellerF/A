import com.storyteller_f.a.api.core.PaginationQuery
import com.storyteller_f.a.client.core.UploadData
import com.storyteller_f.a.client.core.addReaction
import com.storyteller_f.a.client.core.addReadLog
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createNewTopic
import com.storyteller_f.a.client.core.createRoom
import com.storyteller_f.a.client.core.createTitle
import com.storyteller_f.a.client.core.deleteReaction
import com.storyteller_f.a.client.core.getCommunityInfo
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
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.obj.NewRoom
import com.storyteller_f.shared.obj.NewTitle
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.type.ObjectType
import io.ktor.http.ContentType
import io.ktor.http.defaultForFileExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.writeString
import org.junit.jupiter.api.assertNotNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("LongMethod")
class TopicTest {

    @Test
    fun `test topic search`() {
        test {
            attachSession {
                val newId = createCommunity(NewCommunity("aid", "name")).getOrThrow().id
                val lastTopic =
                    createNewTopic(ObjectType.COMMUNITY, newId, "hello world").getOrThrow()
                createNewTopic(ObjectType.COMMUNITY, newId, "sysroot").getOrThrow()
                val firstTopic =
                    createNewTopic(ObjectType.COMMUNITY, newId, "best world").getOrThrow()
                withContext(Dispatchers.IO) { delay(1000) }

                val topics = searchTopics(1, listOf("world")).getOrThrow()
                topics.data.forEach {
                    assertNotNull(it.extension?.authorInfo)
                }
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
    }

    @Test
    fun `test topic snapshot`() {
        test {
            attachSession {
                val newId = createCommunity(NewCommunity("name", "aid")).getOrThrow().id
                val topicInfo = createNewTopic(ObjectType.COMMUNITY, newId, "hello").getOrThrow()
                getTopicSnapshot(topicInfo.id)
            }
        }
    }

    @Test
    fun `test reaction`() {
        test {
            val emoji = "\uD83D\uDE00"
            val session = attachSession {
                val c = createCommunity(NewCommunity("name", "aid")).getOrThrow()
                val topicInfo = createNewTopic(ObjectType.COMMUNITY, c.id, "hello").getOrThrow()
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
            val topicId = session.custom.id
            attachSession {
                assertFails {
                    addReaction(topicId, emoji).getOrThrow()
                }
                joinCommunity(session.custom.rootId)
                val reactions = getReactions(topicId, 10).getOrThrow()
                assertEquals(1, reactions.data.size)
                assertFalse(reactions.data.first().hasReacted)
            }
            loginSession(session) {
                val reactionInfo = deleteReaction(emoji, topicId).getOrThrow()
                assertEquals(0, reactionInfo.count)
                assertFalse(reactionInfo.hasReacted)
                assertListSize(0, getReactions(topicId, 10))
            }
        }
    }

    @Test
    fun `test create user topic`() {
        test {
            attachSession {
                val media = upload(
                    ObjectTuple(it.uid, ObjectType.USER),
                    UploadData(
                        5,
                        "hello.txt",
                        ContentType.defaultForFileExtension("txt")
                    )
                ) {
                    Buffer().apply {
                        writeString("hello")
                    }
                }
                    .getOrThrow().data.first()
                val info =
                    createNewTopic(
                        ObjectType.USER,
                        it.uid,
                        "![hello.txt](${media.name})"
                    ).getOrThrow()
                assertNotNull(info.extension?.authorInfo)
                val plain = info.content as TopicContent.Plain
                assertEquals(media.fullName, plain.list.first().fullName)
                val topicInfo = getTopicInfo(info.id).getOrThrow()
                assertEquals(
                    media.fullName,
                    (topicInfo.content as TopicContent.Plain).list.first().fullName
                )
                // 查询单个topic
                assertListSize(
                    1,
                    getUserTopics(
                        it.uid,
                        paginationQuery = PaginationQuery(null, null, size = 10)
                    )
                )
                createNewTopic(
                    ObjectType.USER,
                    it.uid,
                    "test"
                ).getOrThrow()
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
    }

    @Test
    fun `test create topic in room`() {
        test {
            val (communityId, publicRoomId) = attachSession {
                val communityId = createCommunity(NewCommunity("test1", "test1")).getOrThrow().id
                val publicRoomId =
                    createRoom(NewRoom("room1", "room1", communityId = communityId)).getOrThrow().id
                communityId to publicRoomId
            }.custom
            val receivedFrame = mutableListOf<RoomFrame>()
            attachSession({ roomFrame, model ->
                receivedFrame.add(roomFrame)
            }) {
                joinCommunity(communityId).getOrThrow()
                val roomInfo = joinRoom(publicRoomId).getOrThrow()
                webSocketClient.useWebSocket {
                    sendMessage(
                        ObjectTuple(roomInfo.id, ObjectType.ROOM),
                        roomInfo.isPrivate,
                        "test",
                        emptyList()
                    )
                }?.join()
                while (true) {
                    if (receivedFrame.size == 1) {
                        break
                    }
                    delay(100)
                }
                assertNotNull((receivedFrame.first() as RoomFrame.NewTopicInfo).topicInfo.extension?.authorInfo)
                assertListSize(
                    1,
                    getRoomTopics(
                        publicRoomId,
                        paginationQuery = PaginationQuery(null, null, size = 10)
                    )
                )
                assertFails {
                    createNewTopic(
                        ObjectType.ROOM,
                        publicRoomId,
                        "forbid use api add topic to room"
                    ).getOrThrow()
                }
            }
            receivedFrame.clear()
        }
    }

    @Test
    fun `test create topic in private room`() {
        test {
            val user1 = attachSession {
                createRoom(NewRoom("room2", "room2")).getOrThrow().id
            }
            val privateRoomId = user1.custom
            val user2 = attachSession {
            }
            loginSession(user1) {
                createTitle(
                    NewTitle("join", TitleType.JOIN, user2.uid, privateRoomId, ObjectType.ROOM, "")
                )
            }
            val receivedFrame = mutableListOf<RoomFrame>()
            loginSession(user2, { roomFrame, model ->
                receivedFrame.add(roomFrame)
            }) {
                joinRoom(privateRoomId).getOrThrow()
                val roomInfo2 = getRoomInfo(privateRoomId).getOrThrow()
                val keys = getRoomMembersPublicKeys(
                    privateRoomId,
                    PaginationQuery(null, size = 10)
                ).getOrThrow().data
                webSocketClient.useWebSocket {
                    sendMessage(
                        ObjectTuple(roomInfo2.id, ObjectType.ROOM),
                        roomInfo2.isPrivate,
                        "hello",
                        keys
                    )
                }?.join()
                while (true) {
                    if (receivedFrame.size == 1) {
                        break
                    }
                    delay(100)
                }
                assertNotNull((receivedFrame.first() as RoomFrame.NewTopicInfo).topicInfo.extension?.authorInfo)
                assertResponse(
                    1,
                    getRoomTopics(
                        privateRoomId,
                        paginationQuery = PaginationQuery(null, null, size = 10)
                    )
                ) {
                    val privateRoomTopicList = it.data
                    assertEquals(1, privateRoomTopicList.size)
                    val id = privateRoomTopicList.first().id
                    getTopicInfo(id).getOrThrow()
                    assertFails {
                        createNewTopic(
                            ObjectType.TOPIC,
                            id,
                            "forbid use api add topic to room"
                        ).getOrThrow()
                    }
                }
            }

            receivedFrame.clear()
        }
    }

    @Test
    fun `test create in user`() {
        test {
            attachSession {
                createNewTopic(ObjectType.USER, it.uid, "hello").getOrThrow()
            }
        }
    }

    @Test
    fun `test recommend`() {
        test {
            val custom = attachSession {
                createCommunity(NewCommunity("c1", "c1")).getOrThrow().id
            }.custom
            val custom2 = attachSession {
                val id = createCommunity(NewCommunity("c2", "c2")).getOrThrow().id
                createNewTopic(ObjectType.COMMUNITY, id, "hello 2").getOrThrow()
                id
            }.custom

            attachSession {
                joinCommunity(custom).getOrThrow()
                repeat(4) {
                    createNewTopic(ObjectType.COMMUNITY, custom, "hello $it").getOrThrow()
                }
            }
            noneSession {
                assertEquals(5, getRecommendTopics(null, 10).getOrThrow().data.size)
            }
            attachSession {
                joinCommunity(custom2).getOrThrow()
                createNewTopic(ObjectType.COMMUNITY, custom2, "only").getOrThrow()
                withContext(Dispatchers.IO) { delay(1000) }
                assertListSize(1, getRecommendTopics(null, 10))
            }
        }
    }

    @Test
    fun `test room last read`() {
        val receivedFrame = mutableListOf<RoomFrame>()
        test {
            attachSession({ roomFrame, model ->
                receivedFrame.add(roomFrame)
            }) {
                val roomInfo = createRoom(NewRoom("r1", "r1")).getOrThrow()
                val keys =
                    getRoomMembersPublicKeys(roomInfo.id, PaginationQuery(null, size = 10)).getOrThrow().data
                webSocketClient.useWebSocket {
                    sendMessage(roomInfo.tuple(), true, "hello", keys)
                }?.join()
                while (true) {
                    if (receivedFrame.size == 1) {
                        break
                    }
                    delay(100)
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
    fun `test community last read`() {
        test {
            attachSession {
                val communityInfo = createCommunity(NewCommunity("r1", "r1")).getOrThrow()
                val topic =
                    createNewTopic(ObjectType.COMMUNITY, communityInfo.id, "hello").getOrThrow()
                addReadLog(
                    UpdateUserRead(
                        communityInfo.tuple(),
                        topic.id
                    )
                ).getOrThrow()
                assertEquals(topic.id, getCommunityInfo(communityInfo.id).getOrThrow().lastRead)
                val subTopic = createNewTopic(ObjectType.TOPIC, topic.id, "world").getOrThrow()
                addReadLog(UpdateUserRead(topic.tuple(), subTopic.id)).getOrThrow()
                assertEquals(subTopic.id, getTopicInfo(topic.id).getOrThrow().lastRead)
            }
        }
    }

    @Test
    fun `test pin topic`() {
        test {
            attachSession {
                val communityInfo = createCommunity(NewCommunity("r1", "r1")).getOrThrow()
                val topic =
                    createNewTopic(ObjectType.COMMUNITY, communityInfo.id, "hello").getOrThrow()
                val pinned = pinTopic(topic.id).getOrThrow()
                assertTrue(pinned.isPin)
                val unpinned = unpinTopic(topic.id).getOrThrow()
                assertFalse(unpinned.isPin)
                val userTopic = createNewTopic(ObjectType.USER, it.uid, "hello").getOrThrow()
                val userPinned = pinTopic(userTopic.id).getOrThrow()
                assertTrue(userPinned.isPin)
                val userUnpinned = unpinTopic(userTopic.id).getOrThrow()
                assertFalse(userUnpinned.isPin)
            }
        }
    }
}
