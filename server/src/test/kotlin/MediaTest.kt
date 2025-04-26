import com.storyteller_f.a.client_lib.copy
import com.storyteller_f.a.client_lib.getMediaList
import com.storyteller_f.a.client_lib.upload
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import io.ktor.http.*
import kotlinx.io.Buffer
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaTest {
    @Test
    fun `test upload media`() {
        test { client, _ ->
            val firstTuple = attachSession(client) {
                val response =
                    client.upload(
                        ObjectTuple(it.uid, ObjectType.USER),
                        5,
                        "hello.txt",
                        ContentType.defaultForFileExtension("txt")
                    ) {
                        Buffer().apply {
                            writeString("hello")
                        }
                    }.getOrThrow()
                assertEquals("${it.uid}/hello.txt", response.data.first().item.name)
                assertListSize(1, client.getMediaList(it.uid, ObjectType.USER))
            }
            attachSession(client) {
                val response = client.copy(firstTuple.uid ob ObjectType.USER, "hello.txt").getOrThrow()
                assertEquals("${it.uid}/hello.txt", response.data.first().item.name)
                assertListSize(1, client.getMediaList(it.uid, ObjectType.USER))
            }

        }
    }
}
