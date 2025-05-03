import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.obj.NewRoom
import com.storyteller_f.shared.obj.NewTitle
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.TitleType
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.writeString
import kotlin.test.*

class TopicTest {

    @Test
    fun `test topic search`() {
        test { client, _ ->
            attachSession(client) {
                val newId = client.createCommunity(NewCommunity("aid", "name")).getOrThrow().id
                val lastTopic = client.createNewTopic(ObjectType.COMMUNITY, newId, "hello world").getOrThrow()
                client.createNewTopic(ObjectType.COMMUNITY, newId, "sysroot").getOrThrow()
                val firstTopic = client.createNewTopic(ObjectType.COMMUNITY, newId, "best world").getOrThrow()
                withContext(Dispatchers.IO) { delay(1000) }

                val topics = client.searchTopics(1, listOf("world")).getOrThrow()
                assertEquals(2, topics.pagination?.total)
                assertEquals(1, topics.data.size)
                assertEquals(firstTopic.id, topics.data.first().id)
                val topics2 = client.searchTopics(1, listOf("world"), nextTopicId = topics.data.first().id).getOrThrow()
                assertEquals(lastTopic.id, topics2.data.first().id)
            }
        }
    }

    @Test
    fun `test topic snapshot`() {
        test { client, _ ->
            attachSession(client) {
                val newId = client.createCommunity(NewCommunity("name", "aid")).getOrThrow().id
                val topicInfo = client.createNewTopic(ObjectType.COMMUNITY, newId, "hello").getOrThrow()
                client.getTopicSnapshot(topicInfo.id)
            }
        }
    }

    @Test
    fun `test reaction`() {
        test { client, _ ->
            val emoji = "\uD83D\uDE00"
            val session = attachSession(client) {
                val c = client.createCommunity(NewCommunity("name", "aid")).getOrThrow()
                val topicInfo = client.createNewTopic(ObjectType.COMMUNITY, c.id, "hello").getOrThrow()
                repeat(4) {
                    val reactionInfo = client.addReaction(topicInfo.id, emoji).getOrThrow()
                    assertEquals(emoji, reactionInfo.emoji)
                    assertTrue(reactionInfo.hasReacted)
                }
                // 测试幂等
                client.addReaction(topicInfo.id, emoji).getOrThrow()
                topicInfo
            }
            val topicId = session.custom.id
            attachSession(client) {
                assertFails {
                    client.addReaction(topicId, emoji).getOrThrow()
                }
                client.joinCommunity(session.custom.rootId)
                val reactions = client.getReactions(topicId).getOrThrow()
                assertEquals(1, reactions.data.size)
                assertFalse(reactions.data.first().hasReacted)
            }
            loginSession(client, session) {
                assertTrue(client.deleteReaction(emoji, topicId).getOrThrow())
                assertListSize(0, client.getReactions(topicId))
            }
        }
    }

    @Test
    fun `test create user topic`() {
        test { client, _ ->
            attachSession(client) {
                val media = client.upload(
                    ObjectTuple(it.uid, ObjectType.USER),
                    5,
                    "hello.txt",
                    ContentType.defaultForFileExtension("txt")
                ) {
                    Buffer().apply {
                        writeString("hello")
                    }
                }
                    .getOrThrow().data.first()
                val info =
                    client.createNewTopic(
                        ObjectType.USER,
                        it.uid,
                        "![hello.txt](${media.noPrefixName})"
                    ).getOrThrow()
                val plain = info.content as TopicContent.Plain
                assertEquals(media.name, plain.list.first().name)
                // 查询单个topic
                assertListSize(1, client.getUserTopics(it.uid, null, 10))
                client.createNewTopic(
                    ObjectType.USER,
                    it.uid,
                    "test"
                ).getOrThrow()
                // 查询多个topic
                assertListSize(2, client.getUserTopics(it.uid, null, 10))
            }
        }
    }

    @Test
    fun `test create topic in room`() {
        val receivedFrame = mutableListOf<RoomFrame>()
        test({
            receivedFrame.add(it)
        }) { client, wsClient ->
            val (communityId, publicRoomId) = attachSession(client) {
                val communityId = client.createCommunity(NewCommunity("test1", "test1")).getOrThrow().id
                val publicRoomId =
                    client.createRoom(NewRoom("room1", "room1", communityId = communityId)).getOrThrow().id
                communityId to publicRoomId
            }.custom
            attachSession(client) {
                client.joinCommunity(communityId).getOrThrow()
                val roomInfo = client.joinRoom(publicRoomId).getOrThrow()
                wsClient.useWebSocket {
                    sendMessage(ObjectTuple(roomInfo.id, ObjectType.ROOM), roomInfo.isPrivate, "test", emptyList())
                }?.join()
                while (true) {
                    if (receivedFrame.size == 1) {
                        break
                    }
                    delay(100)
                }
                assertListSize(1, client.getRoomTopics(publicRoomId, null, 10))
                assertFails {
                    client.createNewTopic(
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
        val receivedFrame = mutableListOf<RoomFrame>()
        test({
            receivedFrame.add(it)
        }) { client, wsClient ->
            val user1 = attachSession(client) {
                val privateRoomId = client.createRoom(NewRoom("room2", "room2")).getOrThrow().id
                privateRoomId
            }
            val privateRoomId = user1.custom
            val user2 = attachSession(client) {
            }
            loginSession(client, user1) {
                client.createTitle(NewTitle("join", TitleType.JOIN, user2.uid, privateRoomId, ObjectType.ROOM, ""))
            }
            loginSession(client, user2) {
                val roomInfo2 = client.getRoomInfo(privateRoomId).getOrThrow()
                val keys = client.requestRoomKeys(privateRoomId, null, 10).getOrThrow().data
                wsClient.useWebSocket {
                    sendMessage(ObjectTuple(roomInfo2.id, ObjectType.ROOM), roomInfo2.isPrivate, "hello", keys)
                }?.join()
                while (true) {
                    if (receivedFrame.size == 2) {
                        break
                    }
                    delay(100)
                }
                assertResponse(1, client.getRoomTopics(privateRoomId, null, 10)) {
                    val privateRoomTopicList = it.data
                    assertEquals(1, privateRoomTopicList.size)
                    val id = privateRoomTopicList.first().id
                    client.getTopicInfo(id).getOrThrow()
                    assertFails {
                        client.createNewTopic(ObjectType.TOPIC, id, "forbid use api add topic to room").getOrThrow()
                    }
                }
            }

            receivedFrame.clear()
        }
    }

    @Test
    fun `test create in user`() {
        test { client, _ ->
            attachSession(client) {
                client.createNewTopic(ObjectType.USER, it.uid, "hello").getOrThrow()
            }
        }
    }

    @Test
    fun `test recommend`() {
        test { client, _ ->
            val custom = attachSession(client) {
                client.createCommunity(NewCommunity("c1", "c1")).getOrThrow().id
            }.custom
            val custom2 = attachSession(client) {
                val id = client.createCommunity(NewCommunity("c2", "c2")).getOrThrow().id
                client.createNewTopic(ObjectType.COMMUNITY, id, "hello 2").getOrThrow()
                id
            }.custom

            attachSession(client) {
                client.joinCommunity(custom).getOrThrow()
                repeat(4) {
                    client.createNewTopic(ObjectType.COMMUNITY, custom, "hello $it").getOrThrow()
                }
            }
            assertEquals(5, client.getRecommendTopics(null, 10).getOrThrow().data.size)
            attachSession(client) {
                client.joinCommunity(custom2).getOrThrow()
                client.createNewTopic(ObjectType.COMMUNITY, custom2, "only").getOrThrow()
                withContext(Dispatchers.IO) { delay(1000) }
                assertListSize(1, client.getRecommendTopics(null, 10))
            }
        }
    }

    @Test
    fun `test room last read`() {
        val receivedFrame = mutableListOf<RoomFrame>()
        test({
            receivedFrame.add(it)
        }) { client, socket ->
            attachSession(client) {
                val roomInfo = client.createRoom(NewRoom("r1", "r1")).getOrThrow()
                val keys = client.requestRoomKeys(roomInfo.id, null, 10).getOrThrow().data
                socket.useWebSocket {
                    sendMessage(roomInfo.tuple(), true, "hello", keys)
                }?.join()
                while (true) {
                    if (receivedFrame.size == 1) {
                        break
                    }
                    delay(100)
                }
                val topicId = (receivedFrame.first() as RoomFrame.NewTopicInfo).topicInfo.id
                client.addReadLog(
                    UpdateUserRead(
                        roomInfo.tuple(),
                        topicId
                    )
                ).getOrThrow()
                assertEquals(topicId, client.getRoomInfo(roomInfo.id).getOrThrow().lastRead)
                receivedFrame.clear()
            }
        }
    }

    @Test
    fun `test community last read`() {
        test { client, socket ->
            attachSession(client) {
                val communityInfo = client.createCommunity(NewCommunity("r1", "r1")).getOrThrow()
                val topic = client.createNewTopic(ObjectType.COMMUNITY, communityInfo.id, "hello").getOrThrow()
                client.addReadLog(
                    UpdateUserRead(
                        communityInfo.tuple(),
                        topic.id
                    )
                ).getOrThrow()
                assertEquals(topic.id, client.getCommunityInfo(communityInfo.id).getOrThrow().lastRead)
                val subTopic = client.createNewTopic(ObjectType.TOPIC, topic.id, "world").getOrThrow()
                client.addReadLog(UpdateUserRead(topic.tuple(), subTopic.id)).getOrThrow()
                assertEquals(subTopic.id, client.getTopicInfo(topic.id).getOrThrow().lastRead)
            }
        }
    }
}
