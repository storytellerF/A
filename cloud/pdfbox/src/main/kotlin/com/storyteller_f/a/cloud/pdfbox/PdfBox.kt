package com.storyteller_f.a.cloud.pdfbox

import com.storyteller_f.a.cloud.pdf.PdfService
import com.storyteller_f.a.cloud.pdf.SnapshotVerify
import com.storyteller_f.a.cloud.pdf.getFont
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.astNode
import com.storyteller_f.shared.utils.extractImageUrl
import com.storyteller_f.shared.utils.now
import org.apache.pdfbox.examples.signature.CreateSignature
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.FontMappers
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.acceptChildren
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.visitors.Visitor
import rst.pdfbox.layout.elements.Document
import rst.pdfbox.layout.elements.ImageElement
import rst.pdfbox.layout.elements.Paragraph
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import kotlin.collections.get

class PdfBox : PdfService {
    override fun generateSignedSnapshot(
        creatorInfo: UserInfo,
        authorInfo: UserInfo,
        content: String,
        topicInfo: TopicInfo,
        map: Map<String, File>,
        snapshotVerify: SnapshotVerify
    ): Result<Unit> {
        val saveToFile = snapshotVerify.path
        Document(50f, 50f, 50f, 50f).apply {
            val creatorId = if (creatorInfo.aid == null) creatorInfo.address else creatorInfo.aid
            val authorId = if (authorInfo.aid == null) authorInfo.address else authorInfo.aid
            val font = loadSystemFont(pdDocument, content)
            add(Paragraph().apply {
                addText("pub by $authorId", 14f, font)
            })
            add(Paragraph().apply {
                addText("pub at ${topicInfo.createdTime}", 14f, font)
            })
            add(Paragraph().apply {
                addText("capture by $creatorId", 14f, font)
            })
            add(Paragraph().apply {
                addText("capture at ${now()}", 14f, font)
            })
            val parsedTree = astNode(content)
            parsedTree.accept(object : Visitor {
                override fun visitNode(node: ASTNode) {
                    val type = node.type
                    when (type) {
                        MarkdownElementTypes.PARAGRAPH -> {
                            node.acceptChildren(this)
                        }

                        MarkdownTokenTypes.Companion.TEXT -> {
                            add(Paragraph().apply {
                                addText(node.getTextInNode(content).toString(), 14f, font)
                            })
                        }

                        MarkdownElementTypes.IMAGE -> {
                            val name = extractImageUrl(node, content)
                            add(ImageElement(map[name]!!.canonicalPath))
                        }

                        MarkdownElementTypes.MARKDOWN_FILE -> {
                            node.acceptChildren(this)
                        }
                    }
                }
            })
            save(saveToFile)
        }
        when (snapshotVerify) {
            is SnapshotVerify.KeyStoreVerify -> {
                val password = snapshotVerify.password
                val store = KeyStore.getInstance("PKCS12").apply {
                    load(FileInputStream(snapshotVerify.keyStorePath), password.toCharArray())
                }
                CreateSignature(store, password.toCharArray())
                    .signDetached(saveToFile, snapshotVerify.signedFile, "https://freetsa.org/tsr")
            }

            is SnapshotVerify.NoneVerify -> Unit
        }
        return UNIT_RESULT
    }
}

fun loadSystemFont(
    document: PDDocument,
    content: String,
): PDType0Font? {
    val font = getFont(content)
    // 使用 PDFBox 加载字体
    return PDType0Font.load(
        document,
        FontMappers.instance().getTrueTypeFont(font?.name, null).font,
        true
    )
}
