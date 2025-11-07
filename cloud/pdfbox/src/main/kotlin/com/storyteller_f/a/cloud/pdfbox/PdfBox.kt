@file:Suppress("ImportOrdering")

package com.storyteller_f.a.cloud.pdfbox

import com.storyteller_f.a.cloud.pdf.PdfGenerationSpec
import com.storyteller_f.a.cloud.pdf.PdfService
import com.storyteller_f.a.cloud.pdf.SnapshotGeneration
import com.storyteller_f.a.cloud.pdf.collectPlainText
import com.storyteller_f.a.cloud.pdf.getFont
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.astNode
import com.storyteller_f.shared.utils.extractImageUrl
import com.storyteller_f.shared.utils.readCodeFence
import org.apache.pdfbox.examples.signature.CreateSignature
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.FontMappers
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.acceptChildren
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.visitors.Visitor
import rst.pdfbox.layout.elements.Document
import rst.pdfbox.layout.elements.Frame
import rst.pdfbox.layout.elements.ImageElement
import rst.pdfbox.layout.elements.Paragraph
import rst.pdfbox.layout.shape.RoundRect
import rst.pdfbox.layout.text.TextFlow
import java.awt.Color
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore

class PdfBox : PdfService {
    override fun generateSignedSnapshot(
        creatorInfo: UserInfo,
        authorInfo: UserInfo,
        content: String,
        map: Map<String, File>,
        snapshotGeneration: SnapshotGeneration,
        pdfGenerationSpec: PdfGenerationSpec
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
                addText("pub at ${pdfGenerationSpec.created}", 14f, font)
            })
            add(Paragraph().apply {
                addText("capture by $creatorId", 14f, font)
            })
            add(Paragraph().apply {
                addText("capture at ${pdfGenerationSpec.captured}", 14f, font)
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

data class PdfFontBundle(
    val plain: PDFont,
    val bold: PDFont,
    val italic: PDFont,
    val boldItalic: PDFont
)

private fun loadFontBundle(document: PDDocument, content: String): PdfFontBundle {
    val base = getFont(content)!!
    val env = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
    val candidates = env.allFonts.filter { it.family == base.family }
    fun loadByName(f: java.awt.Font?): PDFont = PDType0Font.load(
        document,
        FontMappers.instance().getTrueTypeFont((f ?: base).name, null).font,
        true
    )

    val bold = candidates.firstOrNull {
        it.name.contains("Bold") && !it.name.contains("Italic")
    }
    val italic = candidates.firstOrNull {
        it.name.contains("Italic") && !it.name.contains("Bold")
    }
    val boldItalic = candidates.firstOrNull {
        it.name.contains("Bold") && it.name.contains("Italic")
    }
    return PdfFontBundle(
        loadByName(base),
        loadByName(bold),
        loadByName(italic),
        loadByName(boldItalic)
    )
}

private fun headerFontSizeFor(type: IElementType): Float = when (type) {
    MarkdownElementTypes.ATX_1 -> 24f
    MarkdownElementTypes.ATX_2 -> 22f
    MarkdownElementTypes.ATX_3 -> 20f
    MarkdownElementTypes.ATX_4 -> 18f
    MarkdownElementTypes.ATX_5 -> 16f
    MarkdownElementTypes.ATX_6 -> 15f
    else -> 18f
}

class PdfBoxVisitor(
    val font: PDFont,
    val content: String,
    val map: Map<String, File>,
    val document: Document
) : Visitor {
    private val fontBundle: PdfFontBundle by lazy { loadFontBundle(document.pdDocument, content) }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override fun visitNode(node: ASTNode) {
        val type = node.type
        when (type) {
            MarkdownElementTypes.MARKDOWN_FILE -> node.acceptChildren(this)

            MarkdownElementTypes.PARAGRAPH -> {
                val p = Paragraph()
                val tf = TextFlow()
                node.acceptChildren(ParagraphVisitor(tf, content, fontBundle))
                p.add(tf)
                document.add(p)
            }

            MarkdownElementTypes.ATX_1,
            MarkdownElementTypes.ATX_2,
            MarkdownElementTypes.ATX_3,
            MarkdownElementTypes.ATX_4,
            MarkdownElementTypes.ATX_5,
            MarkdownElementTypes.ATX_6 -> {
                val headerText = collectPlainText(node, content)
                if (headerText.isNotBlank()) {
                    val size = headerFontSizeFor(type)
                    document.add(Paragraph().apply { addText(headerText, size, font) })
                }
            }

            MarkdownElementTypes.UNORDERED_LIST -> {
                val items = node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
                for (item in items) {
                    val text = collectPlainText(item, content)
                    if (text.isNotBlank()) {
                        document.add(Paragraph().apply { addText("• $text", 14f, font) })
                    }
                }
            }

            MarkdownElementTypes.ORDERED_LIST -> {
                val items = node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
                items.forEachIndexed { index, item ->
                    val text = collectPlainText(item, content)
                    if (text.isNotBlank()) {
                        document.add(Paragraph().apply {
                            addText(
                                "${index + 1}. $text",
                                14f,
                                font
                            )
                        })
                    }
                }
            }

            MarkdownElementTypes.CODE_FENCE -> {
                val code = readCodeFence(node, content).trimIndent()
                if (code.isNotBlank()) {
                    val codeParagraph = Paragraph().apply {
                        val tf = TextFlow()
                        tf.addText(code, 12f, fontBundle.plain)
                        add(tf)
                    }
                    val frame = Frame(codeParagraph)
                    frame.shape = RoundRect(8f)
                    frame.backgroundColor = Color(0xF0, 0xF0, 0xF0)
                    frame.setPadding(10f, 10f, 8f, 8f)
                    frame.setMargin(6f, 6f, 6f, 6f)
                    document.add(frame)
                }
            }

            MarkdownElementTypes.CODE_BLOCK -> {
                val code = node.getTextInNode(content).toString().trimIndent()
                if (code.isNotBlank()) {
                    val codeParagraph = Paragraph().apply {
                        val tf = TextFlow()
                        tf.addText(code, 12f, fontBundle.plain)
                        add(tf)
                    }
                    val frame = Frame(codeParagraph)
                    frame.shape = RoundRect(8f)
                    frame.backgroundColor = Color(0xF0, 0xF0, 0xF0)
                    frame.setPadding(10f, 10f, 8f, 8f)
                    frame.setMargin(6f, 6f, 6f, 6f)
                    document.add(frame)
                }
            }

            MarkdownElementTypes.BLOCK_QUOTE -> {
                val flow = TextFlow()
                node.acceptChildren(QuoteVisitor(content, flow, fontBundle))
                val frame = Frame(Paragraph().apply { add(flow) })
                frame.shape = RoundRect(6f)
                frame.setPadding(10f, 10f, 8f, 8f)
                frame.setMargin(6f, 6f, 6f, 6f)
                document.add(frame)
            }

            MarkdownElementTypes.IMAGE -> {
                val name = extractImageUrl(node, content)
                document.add(ImageElement(map[name]!!.canonicalPath))
            }
        }
    }
}

private class ParagraphVisitor(
    private val flow: TextFlow,
    private val content: String,
    private val fontBundle: PdfFontBundle
) : Visitor {
    override fun visitNode(node: ASTNode) {
        when (node.type) {
            MarkdownTokenTypes.TEXT -> {
                val text = node.getTextInNode(content).toString()
                flow.addText(text, 14f, fontBundle.plain)
            }

            MarkdownElementTypes.EMPH -> addStyledBlock(node, fontBundle.italic, 1)
            MarkdownElementTypes.STRONG -> addStyledBlock(node, fontBundle.bold, 2)

            MarkdownElementTypes.CODE_SPAN -> {
                val raw = node.getTextInNode(content).toString()
                val text = raw.replace("`", "").trim()
                flow.addText(text, 14f, fontBundle.plain)
            }

            MarkdownTokenTypes.WHITE_SPACE -> flow.addText(" ", 14f, fontBundle.plain)

            MarkdownElementTypes.INLINE_LINK,
            MarkdownElementTypes.FULL_REFERENCE_LINK,
            MarkdownElementTypes.SHORT_REFERENCE_LINK -> addLink(node)
        }
    }

    private fun addStyledBlock(node: ASTNode, font: PDFont, offset: Int) {
        val text = node.getTextInNode(content).toString()
        val actual = text.substring(offset, text.length - offset)
        flow.addText(actual, 14f, font)
    }

    private fun addLink(node: ASTNode) {
        val raw = node.getTextInNode(content).toString()
        val match = Regex("\\[([^]]+)]\\(([^)]+)\\)").find(raw)
        val text = match?.groupValues?.getOrNull(1)?.trim()
        if (!text.isNullOrEmpty()) {
            flow.addText(text, 14f, fontBundle.plain)
        } else {
            flow.addText(raw, 14f, fontBundle.plain)
        }
    }
}

class QuoteVisitor(private val content: String, val paragraph: TextFlow, val font: PdfFontBundle) :
    Visitor {
    override fun visitNode(node: ASTNode) {
        println("quota ${node.type}")
        if (node.type == MarkdownTokenTypes.BLOCK_QUOTE) {
            node.acceptChildren(this)
        } else if (node.type == MarkdownElementTypes.PARAGRAPH) {
            val value = node.getTextInNode(content).toString()
                .trimMargin("> ").replace("\n", " ") + "\n\n"
            paragraph.addText(value, 14f, font.italic)
        }
    }
}
