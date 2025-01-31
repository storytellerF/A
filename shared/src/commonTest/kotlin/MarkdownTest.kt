import com.storyteller_f.shared.utils.*
import com.storyteller_f.shared.utils.extractMarkdownHeadline
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.acceptChildren
import org.intellij.markdown.ast.visitors.Visitor
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
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

    @Test
    fun `test extract inline math`() {
        val markdownText = "$\\`\\sqrt{3x-1}+(1+x)^2\\`$".trimIndent()
        val parsedTree = astNode(
            markdownText
        )
        parsedTree.accept(object : Visitor {
            override fun visitNode(node: ASTNode) {
                println(node.type)
                if (node.type == GFMElementTypes.INLINE_MATH) {
                    println(readInlineMath(node, markdownText))
                }
                node.acceptChildren(this)
            }

        })
    }

}

