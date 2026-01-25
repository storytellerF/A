package com.storyteller_f.a.cloud.pdf

import com.storyteller_f.shared.model.UserInfo
import kotlinx.datetime.LocalDateTime
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.acceptChildren
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.visitors.Visitor
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.io.File

sealed class SnapshotGeneration(open val path: File) {
    class KeyStoreGeneration(
        val keyStorePath: String,
        val password: String,
        override val path: File,
        val signedFile: File,
    ) : SnapshotGeneration(path)

    class SimpleGeneration(override val path: File) : SnapshotGeneration(path)
}

class PdfGenerationSpec(val created: LocalDateTime, val captured: LocalDateTime)

interface PdfService {
    fun generateSignedSnapshot(
        creatorInfo: UserInfo,
        authorInfo: UserInfo,
        content: String,
        map: Map<String, File>,
        snapshotGeneration: SnapshotGeneration,
        pdfGenerationSpec: PdfGenerationSpec,
    ): Result<Unit>
}

fun getFont(): Font {
    val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    return graphicsEnvironment.allFonts.firstOrNull {
        it.family == "Noto Serif"
    } ?: error("Noto Serif not found")
}

fun collectPlainText(node: ASTNode, content: String): String {
    val sb = StringBuilder()
    node.acceptChildren(object : Visitor {
        override fun visitNode(node: ASTNode) {
            println("plain text: ${node.type} ${node.getTextInNode(content)}")
            if (node.type == MarkdownTokenTypes.TEXT) {
                sb.append(node.getTextInNode(content))
            } else {
                node.acceptChildren(this)
            }
        }
    })
    return sb.toString().trim()
}
