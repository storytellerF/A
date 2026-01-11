import com.storyteller_f.shared.utils.checkContent
import com.storyteller_f.shared.utils.safeFirstUnicode
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentTest : PlatformHeadlessTest() {
    @Test
    fun `test check content`() {
        checkContent("a").getOrThrow()
        checkContent("👌").getOrThrow()
        checkContent("\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66").getOrThrow()
        checkContent("\n").getOrThrow()
        checkContent("\uf092").getOrThrow()
        assertEquals("👌", safeFirstUnicode("👌abc"))
    }
}
