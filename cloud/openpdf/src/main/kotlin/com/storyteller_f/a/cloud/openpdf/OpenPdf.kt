package com.storyteller_f.a.cloud.openpdf

import com.storyteller_f.a.cloud.core.pdf.PdfService
import com.storyteller_f.a.cloud.core.pdf.getFont
import com.storyteller_f.a.cloud.core.service.SnapshotVerify
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
import org.openpdf.text.FontFactory
import org.openpdf.text.Image
import org.openpdf.text.Paragraph
import org.openpdf.text.pdf.PdfWriter
import java.io.File
import kotlin.collections.get

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
                val creatorId = if (creatorInfo.aid == null) creatorInfo.address else creatorInfo.aid
                val authorId = if (authorInfo.aid == null) authorInfo.address else authorInfo.aid
                add(Paragraph("pub by $authorId", font))
                add(Paragraph("pub at ${topicInfo.createdTime}", font))
                add(Paragraph("capture by $creatorId", font))
                add(Paragraph("capture at ${now()}", font))
                val parsedTree = astNode(content)
                parsedTree.accept(object : Visitor {
                    override fun visitNode(node: ASTNode) {
                        val type = node.type
                        when (type) {
                            MarkdownElementTypes.PARAGRAPH -> {
                                node.acceptChildren(this)
                            }

                            MarkdownTokenTypes.Companion.TEXT -> {
                                add(Paragraph(node.getTextInNode(content).toString(), font))
                            }

                            MarkdownElementTypes.IMAGE -> {
                                val name = extractImageUrl(node, content)
                                add(Image.getInstance(map[name]!!.readBytes()))
                            }

                            MarkdownElementTypes.MARKDOWN_FILE -> {
                                node.acceptChildren(this)
                            }
                        }
                    }
                })
                close()
            }
        }
        return UNIT_RESULT
    }
}
