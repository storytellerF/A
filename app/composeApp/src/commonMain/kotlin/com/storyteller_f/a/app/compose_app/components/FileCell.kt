package com.storyteller_f.a.app.compose_app.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.storyteller_f.a.app.compose_app.LocalAppNavFactory
import com.storyteller_f.a.app.core.components.CustomIcon
import com.storyteller_f.a.app.core.components.IconRes
import com.storyteller_f.a.app.core.components.setText
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.utils.formatTime
import kotlinx.coroutines.launch
import nl.jacobras.humanreadable.HumanReadable
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun FileCell(
    fileInfo: FileInfo?,
    clickItem: (List<FileInfo>) -> Unit
) {
    if (fileInfo != null) {
        var expanded by remember { mutableStateOf(false) }

        Row(modifier = Modifier.fillMaxWidth().combinedClickable(onLongClick = {
            expanded = true
        }) {
            clickItem(listOf(fileInfo))
        }) {
            FileIcon(fileInfo)
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(fileInfo.name, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(10.dp))
                Row {
                    Text(
                        fileInfo.lastModified.formatTime(),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        HumanReadable.fileSize(fileInfo.size),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (fileInfo.contentType.startsWith("image")) {
                    Spacer(modifier = Modifier.height(5.dp))
                    fileInfo.dimension?.let {
                        Text(
                            "w${it.width}·h${it.height}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        FileCellMenu(expanded, {
            expanded = it
        }, fileInfo)
    }
}

@Composable
fun FileCellMenu(expanded: Boolean, updateExpanded: (Boolean) -> Unit, fileInfo: FileInfo) {
    val appNavFactory = LocalAppNavFactory.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            updateExpanded(false)
        }
    ) {
        DropdownMenuItem(
            leadingIcon = {
                CustomIcon(IconRes.Vector(Icons.Default.Fullscreen))
            },
            text = { Text("View") },
            onClick = {
                updateExpanded(false)
                appNavFactory.newAppNav().gotoMedia(fileInfo)
            }
        )
        val clipboardManager = LocalClipboard.current
        val scope = rememberCoroutineScope()
        DropdownMenuItem(
            leadingIcon = {
                CustomIcon(IconRes.Vector(Icons.Default.Fullscreen))
            },
            text = { Text("Copy name") },
            onClick = {
                updateExpanded(false)
                scope.launch {
                    clipboardManager.setText(fileInfo.name)
                }
            }
        )
    }
}

@Composable
fun FileIcon(it: FileInfo) {
    val contentType = it.contentType
    val modifier = Modifier.size(40.dp)
    if (contentType.startsWith("image")) {
        AsyncImage(
            it.url,
            it.name,
            modifier = modifier.clip(RoundedCornerShape(5.dp)),
            contentScale = ContentScale.Crop
        )
    } else if (contentType.startsWith("audio")) {
        Icon(Icons.Default.AudioFile, "audio file", modifier)
    } else if (contentType.startsWith("video")) {
        Icon(Icons.Default.VideoFile, "video file", modifier)
    } else {
        Icon(Icons.Default.AttachFile, "other file", modifier)
    }
}

@Composable
fun UploadIcon(contentType: String) {
    val modifier = Modifier.padding(4.dp)
    when {
        contentType.startsWith("audio") -> Icon(
            Icons.Default.AudioFile,
            contentDescription = "audio"
        )

        contentType.startsWith("video") -> Icon(
            Icons.Default.VideoFile,
            contentDescription = "video"
        )

        else -> Icon(Icons.Default.AttachFile, contentDescription = "file", modifier = modifier)
    }
}
