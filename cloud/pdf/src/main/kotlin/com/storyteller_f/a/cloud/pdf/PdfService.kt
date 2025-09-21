package com.storyteller_f.a.cloud.pdf

import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.io.File

sealed class SnapshotVerify(open val path: File) {
    class KeyStoreVerify(
        val keyStorePath: String,
        val password: String,
        override val path: File,
        val signedFile: File,
    ) : SnapshotVerify(path)

    class NoneVerify(override val path: File) : SnapshotVerify(path)
}

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

fun getFont(content: String): Font? {
    val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    return graphicsEnvironment.allFonts.firstOrNull {
        it.canDisplayUpTo(content) == -1
    }
}
