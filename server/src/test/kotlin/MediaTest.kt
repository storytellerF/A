import com.storyteller_f.a.client_lib.getMediaList
import com.storyteller_f.a.client_lib.upload
import com.storyteller_f.shared.type.ObjectType
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaTest {
    @Test
    fun `test upload media`() {
        test { client, _ ->
            attachSession(client) {
                val response =
                    client.upload(
                        "hello".toByteArray(),
                        "hello.txt",
                        it.uid,
                        ObjectType.USER,
                        ContentType.defaultForFileExtension("txt")
                    ).getOrThrow()
                assertEquals("${it.uid}/hello.txt", response.data.first().item.name)

                assertListSize(1, client.getMediaList(it.uid, ObjectType.USER))
            }
        }
    }
}
