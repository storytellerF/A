import com.storyteller_f.media.getSvgDimension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MediaTest {
    @Test
    fun `test svg dimension`() {
        val dimension = getSvgDimension("", "64px" to "64px")
        assertNotNull(dimension)
        assertEquals(64, dimension.width)
        assertEquals(64, dimension.height)
    }
}