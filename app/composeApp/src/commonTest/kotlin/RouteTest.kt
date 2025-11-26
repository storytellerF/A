import com.storyteller_f.a.app.pages.topic.TopicRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RouteTest {

    @Test
    fun testRouteMatch() {
        val string = "/room/a/test"

        val result = TopicRoute.Companion.parseRefUri(string)
        assertNotNull(result.first)
        assertEquals("test", result.second["aid"])
    }
}
