package com.storyteller_f.a.cloud.openpdf

import com.storyteller_f.a.cloud.pdf.PdfService
import com.storyteller_f.a.cloud.pdf.SnapshotVerify
import com.storyteller_f.a.cloud.pdf.getFont
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.astNode
import com.storyteller_f.shared.utils.extractImageUrl
import com.storyteller_f.shared.utils.now
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.acceptChildren
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.visitors.Visitor
import org.openpdf.text.Document
import org.openpdf.text.Font
import org.openpdf.text.FontFactory
import org.openpdf.text.Image
import org.openpdf.text.Paragraph
import org.openpdf.text.pdf.PdfWriter
import java.io.File

class OpenPdf : PdfService {
    override fun generateSignedSnapshot(
        creatorInfo: UserInfo,
        authorInfo: UserInfo,
        content: String,
        topicInfo: TopicInfo,
        map: Map<String, File>,
        snapshotVerify: SnapshotVerify
    ): Result<Unit> {
        val saveToFile = snapshotVerify.path
        val fontName = getFont(content)!!.fontName
        saveToFile.outputStream().use {
            Document().apply {
                PdfWriter.getInstance(this, it)
                open()
                val font = FontFactory.getFont(fontName)
                add(Paragraph(content, font))
                val creatorId =
                    if (creatorInfo.aid == null) creatorInfo.address else creatorInfo.aid
                val authorId = if (authorInfo.aid == null) authorInfo.address else authorInfo.aid
                add(Paragraph("pub by $authorId", font))
                add(Paragraph("pub at ${topicInfo.createdTime}", font))
                add(Paragraph("capture by $creatorId", font))
                add(Paragraph("capture at ${now()}", font))
                val parsedTree = astNode(content)
                parsedTree.accept(OpenPdfVisitor(this, font, content, map))
                close()
            }
        }
        return UNIT_RESULT
    }
}

class OpenPdfVisitor(
    private val document: Document,
    private val font: Font,
    val content: String,
    private val map: Map<String, File>
) : Visitor {
    override fun visitNode(node: ASTNode) {
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

            MarkdownElementTypes.MARKDOWN_FILE -> {
                node.acceptChildren(this)
            }
        }
    }
}
