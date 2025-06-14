package com.storyteller_f.a.app.pages.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.storyteller_f.a.app.LocalSessionManager
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.common.StateView
import com.storyteller_f.a.app.common.bottomAppending
import com.storyteller_f.a.app.common.topPrepend
import com.storyteller_f.a.app.compontents.Permission
import com.storyteller_f.a.app.compontents.isPermissionGranted
import com.storyteller_f.a.app.compontents.requestPermission
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.model.OnMediaUploaded
import com.storyteller_f.a.app.model.createMediaListViewModel
import com.storyteller_f.a.app.model.createReactionsViewModel
import com.storyteller_f.a.app.utils.Recorder
import com.storyteller_f.a.client_lib.SessionManager
import com.storyteller_f.a.client_lib.UploadData
import com.storyteller_f.a.client_lib.upload
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.formatTime
import com.storyteller_f.shared.utils.mapIfNotNull
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.*
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import nl.jacobras.humanreadable.HumanReadable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPicker(
    showSheet: Boolean,
    sheetState: SheetState,
    mediaTarget: ObjectTuple,
    support: List<String> = listOf("files", "audio recorder"),
    onClickItem: (List<MediaInfo>) -> Unit,
    hideSheet: () -> Unit
) {
    BaseSheet(showSheet, sheetState, hideSheet) {
        val pagerState = rememberPagerState {
            support.size
        }
        val tabs = listOf(Icons.Default.Cloud to "files", Icons.Default.Mic to "audio recorder").filter {
            support.contains(it.second)
        }
        if (support.size != 1) {
            val currentPage = pagerState.currentPage
            val scope = rememberCoroutineScope()
            PrimaryTabRow(currentPage, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                tabs.forEachIndexed { index, pair ->
                    Tab(currentPage == index, {
                        scope.launch {
                            pagerState.scrollToPage(index)
                        }
                    }) {
                        Icon(pair.first, pair.second, modifier = Modifier.padding(vertical = 10.dp).size(30.dp))
                    }
                }
            }
        }
        HorizontalPager(pagerState, modifier = Modifier.height(300.dp)) {
            if (tabs[it].second == "files") {
                MediaListView(mediaTarget, onClickItem)
            } else {
                AudioRecorder(mediaTarget, onClickItem)
            }
        }
    }
}

@Composable
fun AudioRecorder(
    mediaTarget: ObjectTuple,
    uploadSuccess: (List<MediaInfo>) -> Unit
) {
    val isRecording by Recorder.isRecording
    val isGranted by isPermissionGranted(Permission.Audio)
    Box(modifier = Modifier.fillMaxSize()) {
        RecorderButton(isGranted, isRecording, uploadSuccess, mediaTarget)
        if (!isGranted) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer)) {
                Button({
                    requestPermission(Permission.Audio)
                }, modifier = Modifier.align(Alignment.Center)) {
                    Text(("Provide permission"))
                }
            }
        }
    }
}

@Composable
private fun BoxScope.RecorderButton(
    isGranted: Boolean,
    isRecording: Boolean,
    uploadSuccess: (List<MediaInfo>) -> Unit,
    mediaTarget: ObjectTuple,
) {
    val sessionManager = LocalSessionManager.current
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier.size(150.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape).align(Alignment.Center)
            .clickable {
                scope.launch {
                    if (isGranted) {
                        if (isRecording) {
                            val path = Recorder.stopRecord()
                            Napier.i {
                                "save to $path"
                            }
                            uploadPath(path, sessionManager, mediaTarget).mapIfNotNull {
                                uploadSuccess(it)
                            }
                        } else {
                            globalDialogState.use {
                                Recorder.startRecord()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            Icon(Icons.Default.Stop, "stop record", modifier = Modifier.size(50.dp))
        } else {
            Icon(Icons.Default.PlayArrow, "start record", modifier = Modifier.size(50.dp))
        }
    }
}

@Composable
private fun MediaListView(
    mediaTarget: ObjectTuple,
    clickItem: (List<MediaInfo>) -> Unit,
) {
    val sessionManager = LocalSessionManager.current
    val list = createMediaListViewModel(mediaTarget)
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Row {
            IconButton({
                scope.launch {
                    selectFileAndUpload(mediaTarget, sessionManager) {
                        clickItem(it)
                    }
                }
            }) {
                Icon(Icons.Default.CloudUpload, "upload file")
            }
        }
        val pagingItems = list.flow.collectAsLazyPagingItems()
        StateView(pagingItems, modifier = Modifier.weight(1f)) {
            LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                topPrepend(pagingItems)
                items(pagingItems.itemCount, key = pagingItems.itemKey {
                    it.id
                }) {
                    val item = pagingItems[it]
                    if (item != null) {
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            clickItem(listOf(item))
                        }) {
                            FileIcon(item)
                            Spacer(modifier = Modifier.width(20.dp))
                            Column {
                                Text(item.name, style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row {
                                    Text(item.lastModified.formatTime(), style = MaterialTheme.typography.labelSmall)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        HumanReadable.fileSize(item.size),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                item.dimension?.let {
                                    Text("w${it.width}·h${it.height}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                }
                bottomAppending(pagingItems)
            }
        }
    }
}

@Composable
private fun FileIcon(it: MediaInfo) {
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

private suspend fun selectFileAndUpload(
    mediaTarget: ObjectTuple,
    sessionManager: SessionManager,
    uploadSuccess: (List<MediaInfo>) -> Unit
) {
    globalDialogState.use {
        val f = FileKit.openFilePicker()
        if (f != null) {
            upload(
                sessionManager,
                mediaTarget,
                UploadData(
                    f.size(),
                    f.name,
                    ContentType.defaultForFileExtension(f.extension)
                )
            ) {
                f.source().buffered()
            }
        } else {
            null
        }
    }.getOrNull()?.let {
        uploadSuccess(it)
    }
}

suspend fun uploadPath(
    path: Path,
    sessionManager: SessionManager,
    mediaTarget: ObjectTuple,
): Result<List<MediaInfo>?> {
    val meta = SystemFileSystem.metadataOrNull(path) ?: return Result.success(null)
    return globalDialogState.use {
        upload(
            sessionManager,
            mediaTarget,
            UploadData(meta.size, path.name, ContentType.defaultForFileExtension(path.toString())),
        ) {
            SystemFileSystem.source(path).buffered()
        }
    }
}

suspend fun upload(
    sessionManager: SessionManager,
    mediaTarget: ObjectTuple,
    uploadData: UploadData,
    readStream: () -> Input
): List<MediaInfo> {
    if (uploadData.size <= 100 * 1024 * 1024) {
        val response = sessionManager.upload(mediaTarget, uploadData, readStream).getOrThrow()

        bus.emit(OnMediaUploaded(response.data))
        return response.data
    } else {
        throw Exception("size is null or size too big")
    }
}

fun insertContent(
    it: MediaInfo,
    updateInput: (String) -> Unit,
    input: String
) {
    if (it.contentType.startsWith("image/")) {
        updateInput(
            """$input
![${it.name}](${it.name} "${it.name}")
"""
        )
    } else {
        updateInput(
            """$input
```object
{
    "contentType": "${it.contentType}",
    "name": "${it.name}"
}
```"""
        )
    }
}

@Composable
fun ReactionListPage(topicId: PrimaryKey) {
    val viewModel = createReactionsViewModel(topicId)
    val pagingItems = viewModel.flow.collectAsLazyPagingItems()
    StateView(pagingItems) {
        LazyColumn {
            items(pagingItems.itemCount, pagingItems.itemKey {
                it.emoji
            }) {
                val info = pagingItems[it]
                if (info != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(info.emoji, fontSize = 25.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(info.count.toString())
                    }
                }
            }
        }
    }
}
