import com.storyteller_f.a.app.topic.TopicRoute.Companion.parseRefUri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RouteTest {

    @Test
    fun `test route match`() {
        val string = "/room/a/test"

        val result = parseRefUri(string)
        assertNotNull(result.first)
        assertEquals("test", result.second["aid"])
    }
}