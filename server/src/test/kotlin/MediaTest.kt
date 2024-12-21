import com.storyteller_f.a.client_lib.upload
import com.storyteller_f.shared.type.ObjectType
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaTest {
    @Test
    fun `test upload media`() {
        test { client ->
            attachSession(client) {
                val response =
                    client.upload("hello".toByteArray(), "hello.txt", "txt", it.data4, ObjectType.USER).getOrThrow()
                assertEquals("${it.data4}/hello.txt", response.data.first().item.name)
            }
        }
    }
}
