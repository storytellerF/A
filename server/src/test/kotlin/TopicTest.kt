import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.test.*

class TopicTest {

    @Test
    fun `test topic search`() {
        test { client, _ ->
            val newId = SnowflakeFactory.nextId()
            doCreateCommunity(Community("aid", "name", owner = DEFAULT_PRIMARY_KEY, id = newId, createdTime = now()))
            attachSession(client) {
                client.joinCommunity(newId)
                val lastTopic = client.createNewTopic(ObjectType.COMMUNITY, newId, "hello world").getOrThrow()
                client.createNewTopic(ObjectType.COMMUNITY, newId, "sysroot").getOrThrow()
                val firstTopic = client.createNewTopic(ObjectType.COMMUNITY, newId, "best world").getOrThrow()
                withContext(Dispatchers.IO) { delay(1000) }

                val topics = client.searchTopics(null, 1, listOf("world"), null, null).getOrThrow()
                assertEquals(2, topics.pagination?.total)
                assertEquals(1, topics.data.size)
                assertEquals(firstTopic.id, topics.data.first().id)
                val topics2 = client.searchTopics(topics.data.first().id, 1, listOf("world"), null, null).getOrThrow()
                assertEquals(lastTopic.id, topics2.data.first().id)
            }
        }
    }

    @Test
    fun `test topic snapshot`() {
        test { client, _ ->
            attachSession(client) {
                val newId = SnowflakeFactory.nextId()
                doCreateCommunity(Community("aid", "name", null, DEFAULT_PRIMARY_KEY, null, newId, now())).getOrThrow()
                client.joinCommunity(newId)
                val topicInfo = client.createNewTopic(ObjectType.COMMUNITY, newId, "hello").getOrThrow()
                client.getTopicSnapshot(topicInfo.id)
            }
        }
    }

    @Test
    fun `test reaction`() {
        test { client, _ ->
            val emoji = "\uD83D\uDE00"
            val newCommunity = SnowflakeFactory.nextId()
            val session1 = attachSession(client) {
                doCreateCommunity(
                    Community("aid", "name", owner = DEFAULT_PRIMARY_KEY, id = newCommunity, createdTime = now())
                ).getOrThrow()
                client.joinCommunity(newCommunity)
                val topicInfo = client.createNewTopic(ObjectType.COMMUNITY, newCommunity, "hello").getOrThrow()
                val reactionInfo = client.addReaction(topicInfo.id, emoji).getOrThrow()
                assertEquals(emoji, reactionInfo.emoji)
                assertTrue(reactionInfo.hasReacted)
                // 测试幂等
                client.addReaction(topicInfo.id, emoji).getOrThrow()
                topicInfo
            }
            val topicId = session1.data5.id
            attachSession(client) {
                client.joinCommunity(newCommunity)
                val reactions = client.getReactions(topicId).getOrThrow()
                assertEquals(1, reactions.data.size)
                assertFalse(reactions.data.first().hasReacted)
            }
            loginSession(client, session1) {
                assertTrue(client.deleteReaction(emoji, topicId).getOrThrow())
                assertEquals(0, client.getReactions(topicId).getOrThrow().data.size)
            }
        }
    }

    @Test
    fun `test create user topic`() {
        test { client, _ ->
            attachSession(client) {
                val media = client.upload("hello".toByteArray(), "hello.txt", "txt", it.data4, ObjectType.USER)
                    .getOrThrow().data.first()
                val info =
                    client.createNewTopic(
                        ObjectType.USER,
                        it.data4,
                        "![hello.txt](${media.item.noPrefixName})"
                    ).getOrThrow()
                val plain = info.content as TopicContent.Plain
                assertEquals(media.item.name, plain.list.first().item.name)
            }
        }
    }

    @Test
    fun `test create topic in room`() {
        test { client, wsClient ->
            val communityId = SnowflakeFactory.nextId()
            doCreateCommunity(Community("test1", "test1", null, 0, null, communityId, now()))
            val publicRoomId = SnowflakeFactory.nextId()
            createRoom(Room("room1", "room1", null, 0, communityId, publicRoomId, now())).getOrThrow()
            val privateRoomId = SnowflakeFactory.nextId()
            createRoom(Room("room2", "room2", null, 0, null, privateRoomId, now())).getOrThrow()
            attachSession(client) {
                client.joinCommunity(communityId).getOrThrow()
                val roomInfo = client.joinRoom(publicRoomId).getOrThrow()
                wsClient.useWebSocket {
                    sendMessage(roomInfo, "test", emptyList(), null)
                }?.join()
                withContext(Dispatchers.Default) { delay(1000) }
                assertEquals(1, client.getRoomTopics(publicRoomId, null, 10).getOrThrow().data.size)
                addRoomJoin(privateRoomId, it.data4, now())
                val roomInfo2 = client.getRoomInfo(privateRoomId).getOrThrow()
                val keys = client.requestRoomKeys(privateRoomId, null, 10).getOrThrow().data
                wsClient.useWebSocket {
                    sendMessage(roomInfo2, "hello", keys, null)
                }?.join()
                withContext(Dispatchers.Default) { delay(1000) }
                val privateRoomTopicList = client.getRoomTopics(privateRoomId, null, 10).getOrThrow().data
                assertEquals(1, privateRoomTopicList.size)
                val id = privateRoomTopicList.first().id
                client.getTopicInfo(id).getOrThrow()
                assertFails {
                    client.createNewTopic(ObjectType.TOPIC, id, "forbid use api add topic to room").getOrThrow()
                }
                assertFails {
                    client.createNewTopic(
                        ObjectType.ROOM,
                        publicRoomId,
                        "forbid use api add topic to room"
                    ).getOrThrow()
                }
            }
        }
    }

    @Test
    fun `test create in user`() {
        test { client, _ ->
            attachSession(client) {
                client.createNewTopic(ObjectType.USER, it.data4, "hello").getOrThrow()
            }
        }
    }

    @Test
    fun `test recommend`() {
        test { client, _ ->
            val communityId = SnowflakeFactory.nextId()
            doCreateCommunity(Community("c1", "c1", owner = 0, id = communityId, createdTime = now())).getOrThrow()
            attachSession(client) {
                client.joinCommunity(communityId).getOrThrow()
                repeat(4) {
                    client.createNewTopic(ObjectType.COMMUNITY, communityId, "hello $it").getOrThrow()
                }
            }
            withContext(Dispatchers.IO) { delay(1000) }
            assertEquals(4, client.getRecommendTopics(null, 10).getOrThrow().data.size)
        }
    }
}
