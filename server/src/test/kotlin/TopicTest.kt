import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.obj.NewRoom
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.addRoomJoin
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
                val reactionInfo = client.addReaction(topicInfo.id, emoji).getOrThrow()
                assertEquals(emoji, reactionInfo.emoji)
                assertTrue(reactionInfo.hasReacted)
                // 测试幂等
                client.addReaction(topicInfo.id, emoji).getOrThrow()
                topicInfo
            }
            val topicId = session.custom.id
            attachSession(client) {
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
                    "hello".toByteArray(),
                    "hello.txt",
                    it.uid,
                    ObjectType.USER,
                    ContentType.defaultForFileExtension("txt")
                )
                    .getOrThrow().data.first()
                val info =
                    client.createNewTopic(
                        ObjectType.USER,
                        it.uid,
                        "![hello.txt](${media.item.noPrefixName})"
                    ).getOrThrow()
                val plain = info.content as TopicContent.Plain
                assertEquals(media.item.name, plain.list.first().item.name)
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
            val custom = attachSession(client) {
                val communityId = client.createCommunity(NewCommunity("test1", "test1")).getOrThrow().id
                val publicRoomId =
                    client.createRoom(NewRoom("room1", "room1", communityId = communityId)).getOrThrow().id
                val privateRoomId = client.createRoom(NewRoom("room2", "room2")).getOrThrow().id
                Triple(communityId, publicRoomId, privateRoomId)
            }.custom
            val (communityId, publicRoomId, privateRoomId) = custom
            attachSession(client) {
                client.joinCommunity(communityId).getOrThrow()
                val roomInfo = client.joinRoom(publicRoomId).getOrThrow()
                wsClient.useWebSocket {
                    sendMessage(roomInfo, "test", emptyList(), null)
                }?.join()
                while (true) {
                    if (receivedFrame.size == 1) {
                        break
                    }
                    delay(100)
                }
                assertListSize(1, client.getRoomTopics(publicRoomId, null, 10))
                DatabaseFactory.addRoomJoin(privateRoomId, it.uid, now(), roomInfo.memberCount).getOrThrow()
                val roomInfo2 = client.getRoomInfo(privateRoomId).getOrThrow()
                val keys = client.requestRoomKeys(privateRoomId, null, 10).getOrThrow().data
                wsClient.useWebSocket {
                    sendMessage(roomInfo2, "hello", keys, null)
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
//                client.createNewTopic(ObjectType.COMMUNITY, id, "hello 2").getOrThrow()
                id
            }.custom

            attachSession(client) {
                client.joinCommunity(custom).getOrThrow()
                repeat(4) {
                    client.createNewTopic(ObjectType.COMMUNITY, custom, "hello $it").getOrThrow()
                }
            }
            withContext(Dispatchers.IO) { delay(1000) }
            assertEquals(4, client.getRecommendTopics(null, 10).getOrThrow().data.size)
            attachSession(client) {
                client.joinCommunity(custom2).getOrThrow()
                client.createNewTopic(ObjectType.COMMUNITY, custom2, "only").getOrThrow()
                withContext(Dispatchers.IO) { delay(1000) }
                assertListSize(1, client.getRecommendTopics(null, 10))
            }
        }
    }
}
