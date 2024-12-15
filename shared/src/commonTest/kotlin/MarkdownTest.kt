import com.storyteller_f.shared.utils.extractMarkdownHeadline
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownTest {
    @Test
    fun `test extract headline`() {
        val markdownText = """
        # First Level Header
        
        Some introductory text before the second level header.
        
        Hello.
        
        ## Second Level Header
        
        Content after the second level header.
    """.trimIndent()
        val actual = extractMarkdownHeadline(markdownText)
        assertEquals(
            """
            # First Level Header
            Some introductory text before the second level header.
            Hello.
        """.trimIndent(), actual
        )
    }


}