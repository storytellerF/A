import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.obj.NewRoomTopic
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.ObjectType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomFrameTest {
    @Test
    fun testSerialization() {
        val message: RoomFrame = RoomFrame.Message(NewRoomTopic(ObjectType.ROOM, DEFAULT_PRIMARY_KEY, TopicContent.Plain("test")))
        val string = Json.encodeToString(message)
        val obj = Json.decodeFromString<RoomFrame>(string)
        assertEquals(message, obj)
    }
}