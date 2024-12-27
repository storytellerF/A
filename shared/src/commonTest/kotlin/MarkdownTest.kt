import com.storyteller_f.shared.utils.extractHeadParagraph
import com.storyteller_f.shared.utils.extractMarkdownHeadline
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownTest {
    @Test
    fun `test extract headline`() {
        val markdownText = """
        # First Level Header
        
        Some introductory text before the second level header.
        
        Hello.
        ```c
            print();
        ```
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

        val extractMarkdownHeadline = extractMarkdownHeadline(
            """
            ![7276537454461452288/Camera_XHS_17315769117051040g2sg31a3ct9d77edg5pn7lgd7e81mqe6f528.jpg](7276537454461452288/Camera_XHS_17315769117051040g2sg31a3ct9d77edg5pn7lgd7e81mqe6f528.jpg "7276537454461452288/Camera_XHS_17315769117051040g2sg31a3ct9d77edg5pn7lgd7e81mqe6f528.jpg")
        """.trimIndent()
        )
        assertEquals(
            """![7276537454461452288/Camera_XHS_17315769117051040g2sg31a3ct9d77edg5pn7lgd7e81mqe6f528.jpg](7276537454461452288/Camera_XHS_17315769117051040g2sg31a3ct9d77edg5pn7lgd7e81mqe6f528.jpg "7276537454461452288/Camera_XHS_17315769117051040g2sg31a3ct9d77edg5pn7lgd7e81mqe6f528.jpg")""",
            extractMarkdownHeadline
        )
    }

    @Test
    fun `test extract media`() {
        val text = """
            ![test](/test.jpg "test")
        """.trimIndent()
        val list = extractMarkdownMediaLink(text)
        assertEquals("/test.jpg", list.first())
    }

    @Test
    fun `test extract paragraph`() {
        val r = extractHeadParagraph("test")
        assertEquals("test", r)
    }

}