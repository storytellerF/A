import com.storyteller_f.a.client_lib.getMediaList
import com.storyteller_f.a.client_lib.upload
import com.storyteller_f.shared.type.ObjectType
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaTest {
    @Test
    fun `test upload media`() {
        test { client, _ ->
            attachSession(client) {
                val response =
                    client.upload("hello".toByteArray(), "hello.txt", "txt", it.data4, ObjectType.USER).getOrThrow()
                assertEquals("${it.data4}/hello.txt", response.data.first().item.name)

                assertEquals(1, client.getMediaList(it.data4, ObjectType.USER).getOrThrow().data.size)
            }
        }
    }
}
