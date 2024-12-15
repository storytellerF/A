import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.Community
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TopicTest {

    @Test
    fun `test topic search`() {
        test { client ->
            val newId = SnowflakeFactory.nextId()
            DatabaseFactory.dbQuery {
                Community.new(Community("aid", "name", null, DEFAULT_PRIMARY_KEY, null, newId, now()))
            }
            attachSession(client) {
                client.joinCommunity(newId)
                val lastTopic = client.createNewTopic(ObjectType.COMMUNITY, newId, "hello world").getOrThrow()
                client.createNewTopic(ObjectType.COMMUNITY, newId, "sysroot").getOrThrow()
                val firstTopic = client.createNewTopic(ObjectType.COMMUNITY, newId, "best world").getOrThrow()

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
        test { client ->
            attachSession(client) {
                val newId = SnowflakeFactory.nextId()
                DatabaseFactory.dbQuery {
                    Community.new(Community("aid", "name", null, DEFAULT_PRIMARY_KEY, null, newId, now()))
                }
                client.joinCommunity(newId)
                val topicInfo = client.createNewTopic(ObjectType.COMMUNITY, newId, "hello").getOrThrow()
                client.getTopicSnapshot(topicInfo.id)
            }
        }
    }

    @Test
    fun `test reaction`() {
        test { client ->
            val emoji = "\uD83D\uDE00"
            val newCommunity = SnowflakeFactory.nextId()
            val session1 = attachSession(client) {
                DatabaseFactory.dbQuery {
                    Community.new(Community("aid", "name", null, DEFAULT_PRIMARY_KEY, null, newCommunity, now()))
                }
                client.joinCommunity(newCommunity)
                val topicInfo = client.createNewTopic(ObjectType.COMMUNITY, newCommunity, "hello").getOrThrow()
                val reactionInfo = client.addReaction(topicInfo.id, emoji).getOrThrow()
                assertEquals(emoji, reactionInfo.emoji)
                assertTrue(reactionInfo.hasReacted)
                topicInfo
            }
            attachSession(client) {
                client.joinCommunity(newCommunity)
                val reactions = client.getReactions(session1.data5.id).getOrThrow()
                assertEquals(1, reactions.data.size)
                assertFalse(reactions.data.first().hasReacted)
            }
            loginSession(client, session1) {
                assertTrue(client.deleteReaction(emoji).getOrThrow())
                assertEquals(0, client.getReactions(session1.data5.id).getOrThrow().data.size)
            }
        }
    }
}
