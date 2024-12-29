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
        assertEquals(
            """
            # First Level Header
            Some introductory text before the second level header.
            Hello.
        """.trimIndent(), extractMarkdownHeadline(markdownText)
        )
        assertEquals("*hello*", extractMarkdownHeadline("*hello*"))
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

    @Test
    fun `test extract image`() {
        assertEquals(
            """![I00012733.jpg](I00012733.jpg "I00012733.jpg")""",
            extractMarkdownHeadline(
                """![I00012733.jpg](I00012733.jpg "I00012733.jpg")"""
            )
        )
    }

}