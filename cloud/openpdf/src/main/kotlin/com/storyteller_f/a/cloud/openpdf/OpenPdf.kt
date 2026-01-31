package com.storyteller_f.a.cloud.openpdf

import com.storyteller_f.a.cloud.pdf.PdfGenerationSpec
import com.storyteller_f.a.cloud.pdf.PdfService
import com.storyteller_f.a.cloud.pdf.SnapshotGeneration
import com.storyteller_f.a.cloud.pdf.getFont
import com.storyteller_f.a.cloud.pdf.getMonoFont
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
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.openpdf.text.Chunk
import org.openpdf.text.Document
import org.openpdf.text.Element
import org.openpdf.text.Font
import org.openpdf.text.FontFactory
import org.openpdf.text.Image
import org.openpdf.text.Paragraph
import org.openpdf.text.Rectangle
import org.openpdf.text.pdf.PdfContentByte
import org.openpdf.text.pdf.PdfPCell
import org.openpdf.text.pdf.PdfPCellEvent
import org.openpdf.text.pdf.PdfPTable
import org.openpdf.text.pdf.PdfWriter
import org.openpdf.text.pdf.draw.LineSeparator
import java.awt.Color
import java.io.File

/**
 * Font bundle for OpenPDF with purpose-specific font methods.
 */
class OpenPdfFontBundle(private val baseFont: Font) {
    private val monoFontName: String by lazy {
        getMonoFont().fontName
    }

    /** Base font for normal text */
    fun plainFont(): Font = baseFont

    /** Bold font for headings */
    fun boldFont(sizeBoost: Float = 0f): Font =
        FontFactory.getFont(baseFont.familyname, baseFont.size + sizeBoost, Font.BOLD)

    /** Italic font for quotes */
    fun italicFont(): Font =
        FontFactory.getFont(baseFont.familyname, baseFont.size, Font.ITALIC)

    /** Styled font (bold, italic, strikethrough, etc.) */
    fun styledFont(style: Int): Font =
        FontFactory.getFont(baseFont.familyname, baseFont.size, style)

    /** Monospace font for code */
    fun monoFont(): Font =
        FontFactory.getFont(monoFontName, baseFont.size, Font.NORMAL)

    /** Monospace font with syntax highlight color */
    fun monoFontWithColor(color: Color): Font =
        FontFactory.getFont(monoFontName, baseFont.size, baseFont.style, color)

    /** Monospace bold font for syntax highlight */
    fun monoBoldFont(): Font =
        FontFactory.getFont(monoFontName, baseFont.size, Font.BOLD)

    /** Link font (underlined, blue) */
    fun linkFont(): Font =
        FontFactory.getFont(baseFont.familyname, baseFont.size, Font.UNDERLINE, Color.BLUE)
}

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
            val fontName = getFont().fontName
            saveToFile.outputStream().use {
                Document().apply {
                    PdfWriter.getInstance(this, it)
                    open()
                    val baseFont = FontFactory.getFont(fontName)
                    val fontBundle = OpenPdfFontBundle(baseFont)
                    val creatorId = if (creatorInfo.aid == null) creatorInfo.address else creatorInfo.aid
                    val authorId = if (authorInfo.aid == null) authorInfo.address else authorInfo.aid
                    add(Paragraph("pub by $authorId", fontBundle.plainFont()))
                    add(Paragraph("pub at ${pdfGenerationSpec.created}", fontBundle.plainFont()))
                    add(Paragraph("capture by $creatorId", fontBundle.plainFont()))
                    add(Paragraph("capture at ${pdfGenerationSpec.captured}", fontBundle.plainFont()))
                    val parsedTree = astNode(content)
                    parsedTree.accept(OpenPdfVisitor(this, fontBundle, content, map))
                    close()
                }
            }
        }
    }
}

class OpenPdfVisitor(
    private val document: Document,
    private val fontBundle: OpenPdfFontBundle,
    val content: String,
    private val map: Map<String, File>
) : Visitor {
    private val listTypeStack = mutableListOf<Boolean>() // true = ordered, false = unordered
    private val listCounterStack = mutableListOf<Int>()

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override fun visitNode(node: ASTNode) {
        when (node.type) {
            MarkdownElementTypes.PARAGRAPH -> {
                val paragraph = Paragraph()
                node.acceptChildren(ParagraphVisitor(paragraph, content, fontBundle))
                document.add(paragraph)
            }

            MarkdownTokenTypes.TEXT -> {
                document.add(Paragraph(node.getTextInNode(content).toString(), fontBundle.plainFont()))
            }

            MarkdownElementTypes.IMAGE -> {
                val name = extractImageUrl(node, content)
                document.add(Image.getInstance(map[name]!!.readBytes()))
            }

            MarkdownElementTypes.CODE_FENCE -> {
                val table = buildRoundedCodeBlockTable(buildParagraphFromCodeFence(node))
                document.add(table)
            }

            MarkdownElementTypes.CODE_BLOCK -> {
                val table = buildTableFromCodeBlock(node)
                document.add(table)
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

            MarkdownElementTypes.MARKDOWN_FILE -> {
                node.acceptChildren(this)
            }

            MarkdownTokenTypes.HORIZONTAL_RULE -> {
                val p = Paragraph()
                val line = LineSeparator()
                line.lineWidth = 1f
                line.lineColor = Color.GRAY
                p.add(line)
                document.add(p)
            }

            GFMElementTypes.TABLE -> {
                val table = buildTable(node)
                document.add(table)
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
            paragraph.add(Chunk(codeFence, fontBundle.monoFont()))
            return paragraph
        }
        if (codeHighlights.first().location.start > 0) {
            paragraph.add(Chunk(codeFence.take(codeHighlights.first().location.start), fontBundle.monoFont()))
        }
        codeHighlights.forEachIndexed { i, codeHighlight ->
            addHighlightCodeFence(codeHighlight, paragraph, codeFence, i, codeHighlights)
        }
        return paragraph
    }

    private fun buildTableFromCodeBlock(node: ASTNode): PdfPTable {
        val raw = node.getTextInNode(content).toString()
        val paragraph = Paragraph()
        paragraph.add(Chunk(raw.trimIndent(), fontBundle.monoFont()))
        return buildRoundedCodeBlockTable(paragraph)
    }

    private fun addHighlightCodeFence(
        it: CodeHighlight,
        paragraph: Paragraph,
        codeFence: String,
        i: Int,
        codeHighlights: List<CodeHighlight>
    ) {
        val highlightFont = if (it is ColorHighlight) {
            fontBundle.monoFontWithColor(Color(it.rgb))
        } else {
            fontBundle.monoBoldFont()
        }
        val endIndex = it.location.end
        paragraph.add(Chunk(codeFence.substring(it.location.start, endIndex), highlightFont))
        if (i != codeHighlights.lastIndex) {
            val nextStartIndex = codeHighlights[i + 1].location.start
            if (nextStartIndex > endIndex) {
                paragraph.add(Chunk(codeFence.substring(endIndex, nextStartIndex), highlightFont))
            }
        }
    }

    private fun buildRoundedCodeBlockTable(paragraph: Paragraph): PdfPTable {
        val table = PdfPTable(1)
        table.keepTogether = true
        table.setWidthPercentage(100f)
        table.horizontalAlignment = Element.ALIGN_LEFT
        table.setSpacingBefore(6f)
        table.setSpacingAfter(6f)

        paragraph.alignment = Element.ALIGN_LEFT
        val cell = PdfPCell(paragraph)
        cell.border = Rectangle.NO_BORDER
        cell.horizontalAlignment = Element.ALIGN_LEFT
        // padding: left/right/top/bottom
        val horizontalPadding = 12f
        val verticalPadding = 8f
        cell.paddingLeft = horizontalPadding
        cell.paddingRight = horizontalPadding
        cell.paddingTop = verticalPadding
        cell.paddingBottom = verticalPadding

        cell.cellEvent = RoundedBackgroundCellEvent(
            radius = 6f,
            fillColor = Color(245, 245, 245),
            strokeColor = Color(220, 220, 220)
        )

        table.addCell(cell)
        return table
    }

    private class RoundedBackgroundCellEvent(
        private val radius: Float,
        private val fillColor: Color,
        private val strokeColor: Color
    ) : PdfPCellEvent {
        override fun cellLayout(
            cell: PdfPCell?,
            rect: Rectangle?,
            canvas: Array<PdfContentByte?>?
        ) {
            if (rect == null || canvas == null) return
            val cb = canvas.getOrNull(PdfPTable.BACKGROUNDCANVAS) ?: return
            cb.saveState()
            cb.setColorFill(fillColor)
            cb.setColorStroke(strokeColor)
            cb.roundRectangle(rect.left, rect.bottom, rect.width, rect.height, radius)
            cb.fillStroke()
            cb.restoreState()
        }
    }

    private fun buildBlockQuoteTable(paragraph: Paragraph): PdfPTable {
        val table = PdfPTable(1)
        table.keepTogether = true
        table.setWidthPercentage(100f)
        table.horizontalAlignment = Element.ALIGN_LEFT
        table.setSpacingBefore(6f)
        table.setSpacingAfter(6f)

        paragraph.alignment = Element.ALIGN_LEFT
        val cell = PdfPCell(paragraph)
        cell.border = Rectangle.NO_BORDER
        cell.horizontalAlignment = Element.ALIGN_LEFT

        val horizontalPadding = 12f
        val verticalPadding = 6f
        val barWidth = 3f
        val barGap = 6f
        cell.paddingLeft = horizontalPadding + barWidth + barGap
        cell.paddingRight = horizontalPadding
        cell.paddingTop = verticalPadding
        cell.paddingBottom = verticalPadding

        cell.cellEvent = LeftBarCellEvent(barWidth = barWidth, barColor = Color(180, 180, 180))

        table.addCell(cell)
        return table
    }

    private class LeftBarCellEvent(
        private val barWidth: Float,
        private val barColor: Color
    ) : PdfPCellEvent {
        override fun cellLayout(
            cell: PdfPCell?,
            rect: Rectangle?,
            canvas: Array<PdfContentByte?>?
        ) {
            if (rect == null || canvas == null) return
            val cb = canvas.getOrNull(PdfPTable.BACKGROUNDCANVAS) ?: return
            cb.saveState()
            cb.setColorFill(barColor)
            cb.rectangle(rect.left, rect.bottom, barWidth, rect.height)
            cb.fill()
            cb.restoreState()
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
            1 -> 18f
            2 -> 16f
            3 -> 14f
            4 -> 12f
            5 -> 11f
            else -> 0f
        }
        document.add(Paragraph(text, fontBundle.boldFont(sizeBoost)))
    }

    private fun addListItem(node: ASTNode) {
        val isOrdered = listTypeStack.lastOrNull() ?: false
        val depth = listTypeStack.size
        val counter = listCounterStack.lastOrNull() ?: 0
        val prefix = if (isOrdered) "$counter." else "•"
        val text = node.getTextInNode(content).toString().trim()
        val paragraph = Paragraph("$prefix $text", fontBundle.plainFont())
        paragraph.indentationLeft = 20f * depth
        document.add(paragraph)
        if (isOrdered && listCounterStack.isNotEmpty()) {
            listCounterStack[listCounterStack.lastIndex] = counter + 1
        }
    }

    private fun addBlockQuote(node: ASTNode) {
        val paragraph = Paragraph()
        node.acceptChildren(QuoteVisitor(content, paragraph, fontBundle.italicFont()))
        val table = buildBlockQuoteTable(paragraph)
        document.add(table)
    }
    private fun buildTable(node: ASTNode): PdfPTable {
        // Calculate columns - naive approach, take max cells from first row or header
        // For simplicity, we assume consistent column count or scan first row.
        // We need a visitor to just count columns first? Or just gather data.
        val rows = mutableListOf<List<ASTNode>>()
        node.acceptChildren(object : Visitor {
            override fun visitNode(node: ASTNode) {
                if (node.type == GFMElementTypes.HEADER) {
                    node.acceptChildren(this)
                } else if (node.type == GFMElementTypes.ROW) {
                    // Actually, let's just accept children of ROW and filter for content.
                    // In GFM, cells are separated by |.
                    // The parser structure for ROW contains CELL or text + pipes.
                    // Let's print raw text for debugging if needed, but here we implement.
                    // Assuming node.children contains cell nodes or we split by pipe?
                    // IntelliJ parser creates nodes for cells. checking standard GFM implementation...
                    // It uses GFMTokenTypes.CELL for content.
                    rows.add(node.children.filter { it.type.name == "CELL" })
                }
            }
        })

        val colCount = rows.maxOfOrNull { it.size } ?: 1
        val table = PdfPTable(colCount)
        table.widthPercentage = 100f

        rows.forEachIndexed { index, cells ->
            cells.forEach { cellNode ->
                val cellContent = cellNode.getTextInNode(content).toString().trim()
                val p = Paragraph(cellContent, fontBundle.plainFont())
                val cell = PdfPCell(p)
                if (index == 0 && rows.size > 1) { // Assume first row is header if we found a header node?
                    // Actually we flatted header into rows list.
                    // To distinguish, we should have tracked it.
                    // But usually typical markdown table has header.
                    cell.backgroundColor = Color(230, 230, 230)
                }
                cell.paddingLeft = 5f
                cell.paddingRight = 5f
                cell.paddingTop = 5f
                cell.paddingBottom = 5f
                table.addCell(cell)
            }
            // Fill missing cells if any
            for (i in cells.size until colCount) {
                table.addCell(PdfPCell(Paragraph("")))
            }
        }
        return table
    }
}

class ParagraphVisitor(
    private val paragraph: Paragraph,
    val content: String,
    val fontBundle: OpenPdfFontBundle
) : Visitor {
    override fun visitNode(node: ASTNode) {
        when (node.type) {
            MarkdownTokenTypes.TEXT -> {
                val text = node.getTextInNode(content).toString()
                paragraph.add(text)
            }

            // Emphasis & strong
            MarkdownElementTypes.EMPH -> addStyledBlock(node, Font.ITALIC, 1)
            MarkdownElementTypes.STRONG -> addStyledBlock(node, Font.BOLD, 2)

            // Inline code
            MarkdownElementTypes.CODE_SPAN -> addCodeSpan(node)
            MarkdownTokenTypes.WHITE_SPACE -> paragraph.add(" ")

            // Links (render as text (url)) to avoid PDF-specific anchors
            MarkdownElementTypes.INLINE_LINK,
            MarkdownElementTypes.FULL_REFERENCE_LINK,
            MarkdownElementTypes.SHORT_REFERENCE_LINK -> addLink(node)

            GFMElementTypes.STRIKETHROUGH -> addStyledBlock(node, Font.STRIKETHRU, 2)
        }
    }

    private fun addStyledBlock(node: ASTNode, style: Int, offset: Int) {
        val text = node.getTextInNode(content).toString()
        paragraph.add(Chunk(text.substring(offset, text.length - offset), fontBundle.styledFont(style)))
    }

    private fun addCodeSpan(node: ASTNode) {
        val raw = node.getTextInNode(content).toString()
        val text = raw.replace("`", "").trim()
        paragraph.add(Chunk(text, fontBundle.monoFont()))
    }

    private fun addLink(node: ASTNode) {
        val raw = node.getTextInNode(content).toString()
        val match = Regex("\\[([^]]+)]\\(([^)]+)\\)").find(raw)
        val text = match?.groupValues?.getOrNull(1)?.trim()
        val url = match?.groupValues?.getOrNull(2)?.trim()
        if (!text.isNullOrEmpty() && !url.isNullOrEmpty()) {
            val chunk = Chunk(text, fontBundle.linkFont())
            chunk.setAnchor(url)
            paragraph.add(chunk)
        } else {
            paragraph.add(raw)
        }
    }
}

class QuoteVisitor(private val content: String, val paragraph: Paragraph, val font: Font) : Visitor {
    override fun visitNode(node: ASTNode) {
        if (node.type == MarkdownTokenTypes.BLOCK_QUOTE) {
            node.acceptChildren(this)
        } else if (node.type == MarkdownElementTypes.PARAGRAPH) {
            val content = node.getTextInNode(content).toString().trimMargin("> ").replace("\n", " ") + "\n\n"
            paragraph.add(Chunk(content, font))
        }
    }
}
