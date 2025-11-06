package com.storyteller_f.a.cloud.openpdf

import com.storyteller_f.a.cloud.pdf.PdfGenerationSpec
import com.storyteller_f.a.cloud.pdf.PdfService
import com.storyteller_f.a.cloud.pdf.SnapshotGeneration
import com.storyteller_f.a.cloud.pdf.getFont
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.utils.astNode
import com.storyteller_f.shared.utils.extractImageUrl
import com.storyteller_f.shared.utils.readCodeFence
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.CodeHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxThemes
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.acceptChildren
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.visitors.Visitor
import org.openpdf.text.Chunk
import org.openpdf.text.Document
import org.openpdf.text.Font
import org.openpdf.text.FontFactory
import org.openpdf.text.Image
import org.openpdf.text.Paragraph
import org.openpdf.text.pdf.PdfWriter
import java.awt.Color
import java.io.File

class OpenPdf : PdfService {
    override fun generateSignedSnapshot(
        creatorInfo: UserInfo,
        authorInfo: UserInfo,
        content: String,
        map: Map<String, File>,
        snapshotGeneration: SnapshotGeneration,
        pdfGenerationSpec: PdfGenerationSpec
    ): Result<Unit> {
        val saveToFile = snapshotGeneration.path
        return runCatching {
            val fontName = getFont(content)!!.fontName
            saveToFile.outputStream().use {
                Document().apply {
                    PdfWriter.getInstance(this, it)
                    open()
                    val font = FontFactory.getFont(fontName)
                    val creatorId =
                        if (creatorInfo.aid == null) creatorInfo.address else creatorInfo.aid
                    val authorId =
                        if (authorInfo.aid == null) authorInfo.address else authorInfo.aid
                    add(Paragraph("pub by $authorId", font))
                    add(Paragraph("pub at ${pdfGenerationSpec.created}", font))
                    add(Paragraph("capture by $creatorId", font))
                    add(Paragraph("capture at ${pdfGenerationSpec.captured}", font))
                    val parsedTree = astNode(content)
                    parsedTree.accept(OpenPdfVisitor(this, font, content, map))
                    close()
                }
            }
        }
    }
}

class OpenPdfVisitor(
    private val document: Document,
    private val font: Font,
    val content: String,
    private val map: Map<String, File>
) : Visitor {
    private val listTypeStack = mutableListOf<Boolean>() // true = ordered, false = unordered
    private val listCounterStack = mutableListOf<Int>()

    override fun visitNode(node: ASTNode) {
        println(node.type)
        when (node.type) {
            MarkdownElementTypes.PARAGRAPH -> {
                node.acceptChildren(this)
            }

            MarkdownTokenTypes.TEXT -> {
                document.add(Paragraph(node.getTextInNode(content).toString(), font))
            }

            MarkdownElementTypes.IMAGE -> {
                val name = extractImageUrl(node, content)
                document.add(Image.getInstance(map[name]!!.readBytes()))
            }

            MarkdownElementTypes.CODE_FENCE -> {
                val paragraph = buildParagraphFromCodeFence(node)
                document.add(paragraph)
            }

            // Headings
            MarkdownElementTypes.ATX_1 -> addHeading(node, 1)
            MarkdownElementTypes.ATX_2 -> addHeading(node, 2)
            MarkdownElementTypes.ATX_3 -> addHeading(node, 3)
            MarkdownElementTypes.ATX_4 -> addHeading(node, 4)
            MarkdownElementTypes.ATX_5 -> addHeading(node, 5)
            MarkdownElementTypes.ATX_6 -> addHeading(node, 6)
            MarkdownElementTypes.SETEXT_1 -> addHeading(node, 1, isSetext = true)
            MarkdownElementTypes.SETEXT_2 -> addHeading(node, 2, isSetext = true)

            // Emphasis & strong
            MarkdownElementTypes.EMPH -> addStyledBlock(node, Font.ITALIC)
            MarkdownElementTypes.STRONG -> addStyledBlock(node, Font.BOLD)

            // Inline code
            MarkdownElementTypes.CODE_SPAN -> addCodeSpan(node)

            // Lists
            MarkdownElementTypes.ORDERED_LIST -> {
                listTypeStack.add(true)
                listCounterStack.add(1)
                node.acceptChildren(this)
                listTypeStack.removeAt(listTypeStack.lastIndex)
                listCounterStack.removeAt(listCounterStack.lastIndex)
            }
            MarkdownElementTypes.UNORDERED_LIST -> {
                listTypeStack.add(false)
                listCounterStack.add(0)
                node.acceptChildren(this)
                listTypeStack.removeAt(listTypeStack.lastIndex)
                listCounterStack.removeAt(listCounterStack.lastIndex)
            }
            MarkdownElementTypes.LIST_ITEM -> addListItem(node)

            // Block quote
            MarkdownElementTypes.BLOCK_QUOTE -> addBlockQuote(node)

            // Links (render as text (url)) to avoid PDF-specific anchors
            MarkdownElementTypes.INLINE_LINK,
            MarkdownElementTypes.FULL_REFERENCE_LINK,
            MarkdownElementTypes.SHORT_REFERENCE_LINK -> addLink(node)

            MarkdownElementTypes.MARKDOWN_FILE -> {
                node.acceptChildren(this)
            }
        }
    }

    private fun buildParagraphFromCodeFence(node: ASTNode): Paragraph {
        val paragraph = Paragraph()
        val codeFence = readCodeFence(node, content)
        val codeHighlights = Highlights.Builder().theme(SyntaxThemes.atom(darkMode = false))
            .code(codeFence).build().getHighlights().sortedBy {
                it.location.start
            }
        if (codeHighlights.isEmpty()) {
            paragraph.add(Chunk(codeFence, font))
            return paragraph
        }
        if (codeHighlights.first().location.start > 0) {
            paragraph.add(Chunk(codeFence.take(codeHighlights.first().location.start), font))
        }
        codeHighlights.forEachIndexed { i, codeHighlight ->
            addHighlightCodeFence(codeHighlight, paragraph, codeFence, i, codeHighlights)
        }
        return paragraph
    }

    private fun addHighlightCodeFence(
        it: CodeHighlight,
        paragraph: Paragraph,
        codeFence: String,
        i: Int,
        codeHighlights: List<CodeHighlight>
    ) {
        val font = if (it is ColorHighlight) {
            FontFactory.getFont(
                "Courier",
                font.size,
                font.style,
                Color(it.rgb)
            )
        } else {
            FontFactory.getFont("Courier", font.size, Font.BOLD)
        }
        paragraph.add(
            Chunk(
                codeFence.substring(it.location.start, it.location.end),
                font
            )
        )
        if (i != codeHighlights.lastIndex && codeHighlights[i + 1].location.start > it.location.end) {
            paragraph.add(
                Chunk(
                    codeFence.substring(
                        it.location.end,
                        codeHighlights[i + 1].location.start
                    ),
                    font
                )
            )
        }
    }

    private fun addHeading(node: ASTNode, level: Int, isSetext: Boolean = false) {
        val raw = node.getTextInNode(content).toString().trim()
        val text = if (isSetext) {
            raw.lineSequence().firstOrNull()?.trim().orEmpty()
        } else {
            raw.replace(Regex("^#{1,6}\\s*"), "").trim()
        }
        val sizeBoost = when (level) {
            1 -> 8f
            2 -> 6f
            3 -> 4f
            4 -> 2f
            5 -> 1f
            else -> 0f
        }
        val headerFont = FontFactory.getFont(font.familyname, font.size + sizeBoost, Font.BOLD)
        document.add(Paragraph(text, headerFont))
    }

    private fun addStyledBlock(node: ASTNode, style: Int) {
        val text = node.getTextInNode(content).toString()
        val styledFont = FontFactory.getFont(font.familyname, font.size, style)
        document.add(Paragraph(text, styledFont))
    }

    private fun addCodeSpan(node: ASTNode) {
        val raw = node.getTextInNode(content).toString()
        val text = raw.replace("`", "").trim()
        val codeFont = FontFactory.getFont("Courier", font.size, Font.NORMAL)
        document.add(Paragraph(text, codeFont))
    }

    private fun addListItem(node: ASTNode) {
        val isOrdered = listTypeStack.lastOrNull() ?: false
        val depth = listTypeStack.size
        val counter = listCounterStack.lastOrNull() ?: 0
        val prefix = if (isOrdered) "$counter." else "•"
        val text = node.getTextInNode(content).toString().trim()
        val paragraph = Paragraph("$prefix $text", font)
        paragraph.indentationLeft = 20f * depth
        document.add(paragraph)
        if (isOrdered && listCounterStack.isNotEmpty()) {
            listCounterStack[listCounterStack.lastIndex] = counter + 1
        }
    }

    private fun addBlockQuote(node: ASTNode) {
        val text = node.getTextInNode(content).toString().trim()
        val quoteFont = FontFactory.getFont(font.familyname, font.size, Font.ITALIC)
        val paragraph = Paragraph(text, quoteFont)
        paragraph.indentationLeft = 20f
        document.add(paragraph)
    }

    private fun addLink(node: ASTNode) {
        val raw = node.getTextInNode(content).toString()
        val match = Regex("\\[([^]]+)]\\(([^)]+)\\)").find(raw)
        val text = match?.groupValues?.getOrNull(1)?.trim()
        val url = match?.groupValues?.getOrNull(2)?.trim()
        if (!text.isNullOrEmpty() && !url.isNullOrEmpty()) {
            val linkFont = FontFactory.getFont(font.familyname, font.size, Font.UNDERLINE)
            document.add(Paragraph("$text ($url)", linkFont))
        } else {
            document.add(Paragraph(raw, font))
        }
    }
}
