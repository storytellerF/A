package com.storyteller_f.a.app.compose_app

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.storyteller_f.a.app.compose_app.pages.ClientFile
import com.storyteller_f.a.app.compose_app.pages.UploadPage
import io.ktor.http.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.io.Source
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.lang.ref.WeakReference
import kotlin.uuid.ExperimentalUuidApi

fun getClipFile(itemAt: ClipData.Item, context: Context): ClipFile? {
    val contentResolver: ContentResolver = context.contentResolver

    val query =
        contentResolver.query(
            itemAt.uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )
    if (query == null) {
        return null
    } else {
        return query.use { cursor ->
            if (cursor.moveToFirst()) {
                val name =
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                name to size
            } else {
                null
            }
        }?.let { (name, size) ->
            val type = contentResolver.getType(itemAt.uri) ?: "*/*"
            ClipFile(itemAt, context, name, ContentType.parse(type), size, itemAt.uri.toString())
        }
    }
}

class ClipFile(
    private val itemAt: ClipData.Item,
    context: Context,
    override val name: String,
    override val contentType: ContentType,
    override val size: Long,
    override val path: String
) : ClientFile {
    private val contentResolver: ContentResolver = context.contentResolver

    override val id: String
        get() = itemAt.uri.toString()

    override fun source(): Source {
        val stream =
            contentResolver.openInputStream(itemAt.uri) ?: throw Exception("get stream failed")
        return stream.asSource().buffered()
    }
}

class UploadActivity : ComponentActivity() {
    var binder: FileBinder? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        commonForActivity()
        uploadFromIntent()
        setContent {
            UploadPage()
        }
    }

    private fun getClipData(): ImmutableList<ClipFile> {
        val clipData = intent.clipData
        return if (clipData != null) {
            List(clipData.itemCount) {
                getClipFile(clipData.getItemAt(it), this)
            }.filterNotNull().toImmutableList()
        } else {
            persistentListOf()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        uploadFromIntent()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun uploadFromIntent() {
        val clipData = getClipData()
        val fileBinder = binder
        if (fileBinder == null) {
            val intent1 = Intent(this, FileService::class.java)
            val connection = FileConnection(WeakReference(this), clipData)
            bindService(intent1, connection, BIND_AUTO_CREATE)
            return
        }
        fileBinder.upload(clipData)
    }
}
