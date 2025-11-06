package com.storyteller_f.a.cloud.pdfbox

import com.storyteller_f.a.cloud.pdf.PdfService
import com.storyteller_f.a.cloud.pdf.SnapshotGeneration
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
import org.apache.pdfbox.pdmodel.font.PDFont
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

class PdfBox : PdfService {
    override fun generateSignedSnapshot(
        creatorInfo: UserInfo,
        authorInfo: UserInfo,
        content: String,
        topicInfo: TopicInfo,
        map: Map<String, File>,
        snapshotGeneration: SnapshotGeneration
    ): Result<Unit> {
        val saveToFile = snapshotGeneration.path
        Document(50f, 50f, 50f, 50f).apply {
            val creatorId = if (creatorInfo.aid == null) creatorInfo.address else creatorInfo.aid
            val authorId = if (authorInfo.aid == null) authorInfo.address else authorInfo.aid
            val font = loadSystemFont(pdDocument, content)!!
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
            parsedTree.accept(PdfBoxVisitor(font, content, map, this))
            save(saveToFile)
        }
        when (snapshotGeneration) {
            is SnapshotGeneration.KeyStoreGeneration -> {
                val password = snapshotGeneration.password
                val store = KeyStore.getInstance("PKCS12").apply {
                    load(FileInputStream(snapshotGeneration.keyStorePath), password.toCharArray())
                }
                CreateSignature(store, password.toCharArray())
                    .signDetached(
                        saveToFile,
                        snapshotGeneration.signedFile,
                        "https://freetsa.org/tsr"
                    )
            }

            is SnapshotGeneration.SimpleGeneration -> Unit
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

class PdfBoxVisitor(
    val font: PDFont,
    val content: String,
    val map: Map<String, File>,
    val document: Document
) : Visitor {
    override fun visitNode(node: ASTNode) {
        val type = node.type
        when (type) {
            MarkdownElementTypes.PARAGRAPH -> {
                node.acceptChildren(this)
            }

            MarkdownTokenTypes.TEXT -> {
                document.add(Paragraph().apply {
                    addText(node.getTextInNode(content).toString(), 14f, font)
                })
            }

            MarkdownElementTypes.IMAGE -> {
                val name = extractImageUrl(node, content)
                document.add(ImageElement(map[name]!!.canonicalPath))
            }

            MarkdownElementTypes.MARKDOWN_FILE -> {
                node.acceptChildren(this)
            }
        }
    }
}
