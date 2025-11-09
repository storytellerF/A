package com.storyteller_f.a.app.compose_app

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.storyteller_f.a.app.compose_app.utils.ClientFile
import io.ktor.http.ContentType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.io.Source
import kotlinx.io.asSource
import kotlinx.io.buffered

fun getClipFile(context: Context, uri: Uri): ClipFile? {
    val contentResolver: ContentResolver = context.contentResolver

    val query =
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )
    if (query == null) {
        return null
    }
    val (name, size) = query.use { cursor ->
        if (cursor.moveToFirst()) {
            val name =
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            val size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
            name to size
        } else {
            null
        }
    } ?: return null
    val type = contentResolver.getType(uri) ?: "*/*"
    return ClipFile(
        context,
        name,
        ContentType.parse(type),
        size,
        uri.toString()
    )
}

class ClipFile(
    context: Context,
    override val name: String,
    override val contentType: ContentType,
    override val size: Long,
    override val path: String
) : ClientFile {
    private val contentResolver: ContentResolver = context.contentResolver

    override fun source(): Source {
        val stream =
            contentResolver.openInputStream(path.toUri()) ?: throw Exception("get stream failed")
        return stream.asSource().buffered()
    }
}

class UploadActivity : ComponentActivity(), ClientFileServiceContainer {
    override var binder: FileBinder? = null
    override var isConnecting: Boolean = false
    val receiver = CustomClientFileProvider(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        commonForActivity()
        downloadFromIntent()
        setContent {
            CompositionLocalProvider(
                LocalClientFileProvider provides receiver,
                LocalUiViewModel provides uiViewModel
            ) {
                UploadPage()
            }
        }
    }

    private fun getClipFiles(): ImmutableList<ClipFile> {
        val clipData = intent.clipData
        return if (clipData != null) {
            List(clipData.itemCount) {
                getClipFile(this, clipData.getItemAt(it).uri)
            }.filterNotNull().toImmutableList()
        } else {
            persistentListOf()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        downloadFromIntent()
    }

    private fun downloadFromIntent() {
        val clipData = getClipFiles()
        lifecycleScope.launch {
            receiver.getUploader()?.upload(clipData)
        }
    }
}
