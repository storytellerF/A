package com.storyteller_f.a.cloud.openpdf

import com.storyteller_f.a.cloud.pdf.PdfService
import com.storyteller_f.a.cloud.pdf.SnapshotGeneration
import com.storyteller_f.a.cloud.pdf.getFont
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.utils.astNode
import com.storyteller_f.shared.utils.extractImageUrl
import com.storyteller_f.shared.utils.now
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
        topicInfo: TopicInfo,
        map: Map<String, File>,
        snapshotGeneration: SnapshotGeneration
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
                    add(Paragraph("pub at ${topicInfo.createdTime}", font))
                    add(Paragraph("capture by $creatorId", font))
                    add(Paragraph("capture at ${now()}", font))
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
                font.familyname,
                font.size,
                font.style,
                Color(it.rgb)
            )
        } else {
            FontFactory.getFont(font.familyname, font.size, Font.BOLD)
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
}
