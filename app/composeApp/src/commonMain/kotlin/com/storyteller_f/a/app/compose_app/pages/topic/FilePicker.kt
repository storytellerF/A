package com.storyteller_f.a.app.compose_app.pages.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.storyteller_f.a.app.compose_app.LocalAppNavFactory
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.common.OnMediaUploaded
import com.storyteller_f.a.app.compose_app.common.createMediaListViewModel
import com.storyteller_f.a.app.compose_app.components.BaseSheet
import com.storyteller_f.a.app.compose_app.components.Permission
import com.storyteller_f.a.app.compose_app.components.isPermissionGranted
import com.storyteller_f.a.app.compose_app.components.requestPermission
import com.storyteller_f.a.app.compose_app.utils.Recorder
import com.storyteller_f.a.app.compose_app.utils.setText
import com.storyteller_f.a.app.core.components.CustomIcon
import com.storyteller_f.a.app.core.components.GlobalDialogController
import com.storyteller_f.a.app.core.components.GlobalDialogState
import com.storyteller_f.a.app.core.components.GlobalDialogStateProgress
import com.storyteller_f.a.app.core.components.IconRes
import com.storyteller_f.a.app.core.components.LocalGlobalDialog
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.bottomAppending
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.app.core.components.topPrepend
import com.storyteller_f.a.client.core.UploadData
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.upload
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.utils.formatTime
import com.storyteller_f.shared.utils.generateImageMarkdownContent
import com.storyteller_f.shared.utils.generateObjectMarkdownContent
import com.storyteller_f.shared.utils.mapIfNotNull
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import io.ktor.http.ContentType
import io.ktor.http.defaultForFileExtension
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
    onClickItem: (List<FileInfo>) -> Unit,
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
                        onClickItem(it)
                    }
                }
            }) {
                Icon(Icons.Default.CloudUpload, "upload file")
            }
        }
        StateView(viewModel, modifier = Modifier.weight(1f)) { pagingItems ->
            LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                topPrepend(pagingItems.loadState)
                pagingItems(pagingItems, {
                    it.id
                }) {
                    val item = pagingItems[it]
                    FileCell(item, onClickItem)

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
    input: String,
    updateInput: (String) -> Unit,
) {
    val text = if (it.contentType.startsWith("image/")) {
        "\n${generateImageMarkdownContent(it)}"
    } else {
        "\n${generateObjectMarkdownContent(it)}"
    }
    updateInput(input + text)
}
