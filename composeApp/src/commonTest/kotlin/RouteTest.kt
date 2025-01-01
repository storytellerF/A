import com.storyteller_f.a.app.pages.topic.TopicRoute.Companion.parseRefUri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RouteTest {

    @Test
    fun testRouteMatch() {
        val string = "/room/a/test"

        val result = parseRefUri(string)
        assertNotNull(result.first)
        assertEquals("test", result.second["aid"])
    }
}