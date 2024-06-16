import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.ObjectType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomFrameTest {
    @Test
    fun testSerialization() {
        val message: RoomFrame = RoomFrame.Message(NewTopic(ObjectType.ROOM, 0u, TopicContent.Plain("test")))
        val string = Json.encodeToString(message)
        val obj = Json.decodeFromString<RoomFrame>(string)
        assertEquals(message, obj)
    }
}