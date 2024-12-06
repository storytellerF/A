import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.client_lib.addReaction
import com.storyteller_f.a.client_lib.createNewTopic
import com.storyteller_f.a.client_lib.deleteReaction
import com.storyteller_f.a.client_lib.getReactions
import com.storyteller_f.a.client_lib.getTopicSnapshot
import com.storyteller_f.a.client_lib.joinCommunity
import com.storyteller_f.a.client_lib.searchTopics
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.Community
import io.ktor.client.call.body
import org.apache.fontbox.ttf.OTFParser
import org.apache.pdfbox.io.RandomAccessReadBufferedFile
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType0Font
import java.io.File
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
                val lastTopic = client.createNewTopic(ObjectType.COMMUNITY, newId, "hello world").body<TopicInfo>()
                client.createNewTopic(ObjectType.COMMUNITY, newId, "sysroot")
                val firstTopic = client.createNewTopic(ObjectType.COMMUNITY, newId, "best world").body<TopicInfo>()

                val topics = client.searchTopics(null, 1, listOf("world"), null, null)
                assertEquals(2, topics.pagination?.total)
                assertEquals(1, topics.data.size)
                assertEquals(firstTopic.id, topics.data.first().id)
                val topics2 = client.searchTopics(topics.data.first().id, 1, listOf("world"), null, null)
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
                val topicInfo = client.createNewTopic(ObjectType.COMMUNITY, newId, "hello").body<TopicInfo>()
                client.getTopicSnapshot(topicInfo.id)
            }
        }
    }

    @Test
    fun `test generate pdf`() {
        PDDocument().use { document ->
            val firstPage = PDPage()
            PDPageContentStream(document, firstPage).use { stream ->
                stream.beginText()
                val otf = OTFParser().parse(
                    RandomAccessReadBufferedFile(
                        File(
                            "~/DIN-Regular.otf".replace(
                                "~",
                                System.getProperty("user.home")
                            )
                        )
                    )
                )
                stream.setFont(PDType0Font.load(document, otf, false), 12f)
                stream.newLineAtOffset(100F, 700F)
                stream.setLeading(14.5f)
                stream.showText("hello")
                stream.newLine()
                stream.showText("world")
                stream.endText()
            }
            document.addPage(firstPage)
            document.save("/tmp/test.pdf")
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
                val topicInfo = client.createNewTopic(ObjectType.COMMUNITY, newCommunity, "hello").body<TopicInfo>()
                val reactionInfo = client.addReaction(topicInfo.id, emoji)
                assertEquals(emoji, reactionInfo.emoji)
                assertTrue(reactionInfo.hasReacted)
                topicInfo
            }
            attachSession(client) {
                client.joinCommunity(newCommunity)
                val reactions = client.getReactions(session1.data5.id)
                assertEquals(1, reactions.data.size)
                assertFalse(reactions.data.first().hasReacted)
            }
            loginSession(client, session1) {
                assertTrue(client.deleteReaction(emoji))
                assertEquals(0, client.getReactions(session1.data5.id).data.size)
            }
        }
    }
}
