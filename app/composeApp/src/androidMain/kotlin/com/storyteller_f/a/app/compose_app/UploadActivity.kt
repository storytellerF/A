package com.storyteller_f.a.app.compose_app

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import io.ktor.http.*
import kotlinx.io.Source
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun getClipFile(itemAt: ClipData.Item, context: Context): ClipFile? {
    val contentResolver: ContentResolver = context.contentResolver

    val query =
        contentResolver.query(itemAt.uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
    if (query == null) {
        return null
    } else {
        return query.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                name to size
            } else {
                null
            }
        }?.let { (name, size) ->
            val type = contentResolver.getType(itemAt.uri) ?: "*/*"
            ClipFile(itemAt, context, name, ContentType.parse(type), size)
        }
    }
}

class ClipFile(
    private val itemAt: ClipData.Item,
    context: Context,
    override val name: String,
    override val contentType: ContentType,
    override val size: Long
) : ClientFile {
    private val contentResolver: ContentResolver = context.contentResolver

    override val id: String
        get() = itemAt.uri.toString()

    override fun source(): Source? {
        return contentResolver.openInputStream(itemAt.uri)?.asSource()?.buffered()
    }
}

class UploadActivity : ComponentActivity() {
    private val uploader = Uploader(mutableStateOf(null))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        commonForActivity()
        flushSession()
        setContent {
            Upload(uploader)
        }
    }

    private fun getClipData(): List<ClipFile> {
        val clipData = intent.clipData
        return if (clipData != null) {
            List(clipData.itemCount) {
                getClipFile(clipData.getItemAt(it), this)
            }.filterNotNull()
        } else {
            emptyList()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        flushSession()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun flushSession() {
        if (uploader.session.value == null) {
            uploader.session.value = UploadSession(Uuid.random().toString(), getClipData())
        }
    }
}
