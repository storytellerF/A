package com.storyteller_f.a.app.compose_app.pages.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.storyteller_f.a.app.compose_app.LocalAppNav
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.common.OnMediaUploaded
import com.storyteller_f.a.app.compose_app.common.createMediaListViewModel
import com.storyteller_f.a.app.compose_app.components.*
import com.storyteller_f.a.app.compose_app.utils.Recorder
import com.storyteller_f.a.app.core.compontents.CustomIcon
import com.storyteller_f.a.app.core.compontents.IconRes
import com.storyteller_f.a.app.core.compontents.StateView
import com.storyteller_f.a.app.core.compontents.bottomAppending
import com.storyteller_f.a.app.core.compontents.topPrepend
import com.storyteller_f.a.client.core.UploadData
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.upload
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.utils.formatTime
import com.storyteller_f.shared.utils.mapIfNotNull
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.*
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.io.files.FileMetadata
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import nl.jacobras.humanreadable.HumanReadable
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePicker(
    showSheet: Boolean,
    sheetState: SheetState,
    mediaTarget: ObjectTuple,
    support: List<String> = listOf("files", "audio recorder"),
    onClickItems: (List<FileInfo>) -> Unit,
    hideSheet: () -> Unit,
) {
    BaseSheet(showSheet, sheetState, hideSheet) {
        val pagerState = rememberPagerState {
            support.size
        }
        val tabs =
            listOf(Icons.Default.Cloud to "files", Icons.Default.Mic to "audio recorder").filter {
                support.contains(it.second)
            }
        if (support.size != 1) {
            val currentPage = pagerState.currentPage
            val scope = rememberCoroutineScope()
            PrimaryTabRow(
                currentPage,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                tabs.forEachIndexed { index, pair ->
                    Tab(currentPage == index, {
                        scope.launch {
                            pagerState.scrollToPage(index)
                        }
                    }) {
                        Icon(
                            pair.first,
                            pair.second,
                            modifier = Modifier.padding(vertical = 10.dp).size(30.dp)
                        )
                    }
                }
            }
        }
        HorizontalPager(pagerState, modifier = Modifier.height(300.dp)) {
            if (tabs[it].second == "files") {
                FileListView(mediaTarget, onClickItems)
            } else {
                AudioRecorder(mediaTarget, onClickItems)
            }
        }
    }
}

@Composable
fun AudioRecorder(
    mediaTarget: ObjectTuple,
    uploadSuccess: (List<FileInfo>) -> Unit,
) {
    val isRecording by Recorder.isRecording
    val isGranted by isPermissionGranted(
        Permission.Audio
    )
    Box(modifier = Modifier.fillMaxSize()) {
        RecorderButton(isGranted, isRecording, uploadSuccess, mediaTarget)
        if (!isGranted) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Button({
                    requestPermission(
                        Permission.Audio
                    )
                }, modifier = Modifier.align(Alignment.Center)) {
                    Text("Provide permission")
                }
            }
        }
    }
}

@Composable
private fun BoxScope.RecorderButton(
    isGranted: Boolean,
    isRecording: Boolean,
    uploadSuccess: (List<FileInfo>) -> Unit,
    mediaTarget: ObjectTuple,
) {
    val sessionManager = LocalSessionManager.current
    val scope = rememberCoroutineScope()
    val globalDialogController = LocalGlobalDialog.current
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
                            if (path != null) {
                                globalDialogController.uploadPath(path, sessionManager, mediaTarget)
                                    .mapIfNotNull {
                                        uploadSuccess(it)
                                    }
                            }
                        } else {
                            globalDialogController.useResult {
                                runCatching {
                                    Recorder.startRecord()
                                }
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
private fun FileListView(
    mediaTarget: ObjectTuple,
    clickItem: (List<FileInfo>) -> Unit,
) {
    val sessionManager = LocalSessionManager.current
    val viewModel = createMediaListViewModel(mediaTarget)
    val scope = rememberCoroutineScope()
    val globalDialogController = LocalGlobalDialog.current
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(mediaTarget.toString())
        Row {
            IconButton({
                scope.launch {
                    globalDialogController.selectFileAndUpload(mediaTarget, sessionManager) {
                        clickItem(it)
                    }
                }
            }) {
                Icon(Icons.Default.CloudUpload, "upload file")
            }
        }
        StateView(viewModel, modifier = Modifier.weight(1f)) { pagingItems ->
            LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                topPrepend(pagingItems.loadState)
                items(pagingItems.itemSnapshotList.size, key = pagingItems.itemKey {
                    it.id
                }) {
                    val item = pagingItems[it]
                    FileCell(item, clickItem)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                }
                bottomAppending(pagingItems.loadState)
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun FileCell(
    fileInfo: FileInfo?,
    clickItem: (kotlin.collections.List<FileInfo>) -> Unit
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
                Spacer(modifier = Modifier.height(5.dp))
                fileInfo.dimension?.let {
                    Text(
                        "w${it.width}·h${it.height}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        val appNav = LocalAppNav.current
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            }
        ) {
            DropdownMenuItem(
                leadingIcon = {
                    CustomIcon(IconRes.Vector(Icons.Default.Fullscreen))
                },
                text = { Text("View") },
                onClick = {
                    expanded = false
                    appNav.gotoMedia(fileInfo)
                }
            )
        }
    }
}

@Composable
private fun FileIcon(it: FileInfo) {
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

private suspend fun GlobalDialogController.selectFileAndUpload(
    mediaTarget: ObjectTuple,
    sessionManager: UserSessionManager,
    uploadSuccess: (List<FileInfo>) -> Unit,
) {
    useResult {
        val f = FileKit.openFilePicker()
        if (f != null) {
            upload(
                sessionManager,
                mediaTarget,
                getUploadDataFromPlatformFile(f)
            ) { p, t ->
                emitProgress {
                    GlobalDialogState.Loading(
                        progress = GlobalDialogStateProgress(p, t)
                    )
                }
            }
        } else {
            Result.success(null)
        }
    }.getOrNull()?.let {
        uploadSuccess(it)
    }
}

private fun getUploadDataFromPlatformFile(f: PlatformFile) = UploadData(
    f.size(),
    f.name,
    ContentType.defaultForFileExtension(f.extension)
) {
    f.source().buffered()
}

suspend fun GlobalDialogController.uploadPath(
    path: Path,
    sessionManager: UserSessionManager,
    mediaTarget: ObjectTuple,
): Result<List<FileInfo>?> {
    val meta = SystemFileSystem.metadataOrNull(path) ?: return Result.success(null)
    return useResult {
        upload(
            sessionManager,
            mediaTarget,
            getUploadDataFromPath(meta, path)
        ) { p, t ->
            emitProgress {
                GlobalDialogState.Loading(
                    progress = GlobalDialogStateProgress(p, t)
                )
            }
        }
    }
}

private fun getUploadDataFromPath(
    meta: FileMetadata,
    path: Path
) = UploadData(meta.size, path.name, ContentType.defaultForFileExtension(path.toString())) {
    SystemFileSystem.source(path).buffered()
}

suspend fun GlobalDialogController.upload(
    sessionManager: UserSessionManager,
    mediaTarget: ObjectTuple,
    uploadData: UploadData,
    onUpload: (Long, Long?) -> Unit = { _, _ -> },
): Result<List<FileInfo>> {
    if (uploadData.size > 100 * 1024 * 1024) {
        return Result.failure(Exception("file size is too large"))
    }

    return sessionManager.upload(mediaTarget, uploadData, onUpload).map {
        it.data
    }.onSuccess {
        emitEvent(OnMediaUploaded(it))
    }
}

fun insertContent(
    it: FileInfo,
    updateInput: (String) -> Unit,
    input: String,
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
