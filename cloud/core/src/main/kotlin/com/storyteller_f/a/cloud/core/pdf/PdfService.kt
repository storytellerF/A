package com.storyteller_f.a.cloud.core.pdf

import com.storyteller_f.a.cloud.core.service.SnapshotVerify
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.FontMappers
import org.apache.pdfbox.pdmodel.font.PDType0Font
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.io.File

interface PdfService {
    fun generateSignedSnapshot(
        creatorInfo: UserInfo,
        authorInfo: UserInfo,
        content: String,
        topicInfo: TopicInfo,
        map: Map<String, File>,
        snapshotVerify: SnapshotVerify
    ): Result<Unit>
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

fun getFont(content: String): Font? {
    val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    return graphicsEnvironment.allFonts.firstOrNull {
        it.canDisplayUpTo(content) == -1
    }
}
