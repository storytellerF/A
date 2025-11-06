package com.storyteller_f.a.cloud.pdf

import com.storyteller_f.shared.model.UserInfo
import kotlinx.datetime.LocalDateTime
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

fun getFont(content: String): Font? {
    val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    return graphicsEnvironment.allFonts.firstOrNull {
        it.canDisplayUpTo(content) == -1
    }
}
