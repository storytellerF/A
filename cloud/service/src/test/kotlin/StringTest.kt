import com.storyteller_f.a.cloud.core.service.isVisibleUnicodeString
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringTest {
    @Test
    fun testVisibleUnicodeString() {
        assertTrue(isVisibleUnicodeString("a"))
        assertFalse(isVisibleUnicodeString("\uFEFF"))
        assertFalse(isVisibleUnicodeString("\u0301"))
        assertFalse(isVisibleUnicodeString("\u00A0"))
        assertFalse(isVisibleUnicodeString("\u202F"))
        assertFalse(isVisibleUnicodeString("\u3000"))
        assertFalse(isVisibleUnicodeString("\u200B"))
        assertFalse(isVisibleUnicodeString("\u202A"))
        // U+1AFF is a non-spacing mark
        assertFalse(isVisibleUnicodeString("\u1AFF"))
        assertFalse(isVisibleUnicodeString("\u206F"))
    }
}
