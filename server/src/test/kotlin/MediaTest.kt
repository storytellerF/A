import com.storyteller_f.a.client_lib.copy
import com.storyteller_f.a.client_lib.getMediaList
import com.storyteller_f.a.client_lib.upload
import com.storyteller_f.media.getImageDimension
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.writeString
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MediaTest {
    @Test
    fun `test upload media`() {
        test {
            val firstTuple = attachSession {
                val response =
                    upload(
                        ObjectTuple(it.uid, ObjectType.USER),
                        5,
                        "hello.txt",
                        ContentType.defaultForFileExtension("txt")
                    ) {
                        Buffer().apply {
                            writeString("hello")
                        }
                    }.getOrThrow()
                assertEquals("${it.uid}/hello.txt", response.data.first().name)
                assertListSize(1, getMediaList(it.uid, ObjectType.USER, null, 10))
            }
            attachSession {
                val response = copy(firstTuple.uid ob ObjectType.USER, "hello.txt").getOrThrow()
                assertEquals("${it.uid}/hello.txt", response.data.first().name)
                assertListSize(1, getMediaList(it.uid, ObjectType.USER, null, 10))
            }
        }
    }

    @Test
    fun `get png size`() {
        runTest {
            val bufferedImage = ImageIO.read(ClassLoader.getSystemClassLoader().getResourceAsStream("avatar1.png"))
            assertEquals(bufferedImage.width, 420)
            assertEquals(bufferedImage.height, 420)
            val dimension = getImageDimension("avatar1.png", "image/png") {
                ClassLoader.getSystemClassLoader().getResourceAsStream("avatar1.png")!!
            }
            assertNotNull(dimension)
            assertEquals(dimension.width, 420)
            assertEquals(dimension.height, 420)
        }
    }
}
