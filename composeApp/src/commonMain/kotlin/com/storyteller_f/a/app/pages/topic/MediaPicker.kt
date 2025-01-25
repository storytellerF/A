package com.storyteller_f.a.app.pages.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import com.storyteller_f.a.app.LocalClient
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.common.StateView
import com.storyteller_f.a.app.compontents.Permission
import com.storyteller_f.a.app.compontents.isPermissionGranted
import com.storyteller_f.a.app.compontents.requestPermission
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.model.OnMediaUploaded
import com.storyteller_f.a.app.model.createMediaListViewModel
import com.storyteller_f.a.app.utils.Recorder
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.upload
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.formatTime
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.extension
import io.github.vinceglb.filekit.core.pickFile
import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPicker(
    showSheet: Boolean,
    sheetState: SheetState,
    privateRoomId: PrimaryKey?,
    clickItem: (MediaInfo) -> Unit,
    uploadSuccess: (MediaInfo) -> Unit,
    support: List<String> = listOf("files", "audio recorder"),
    hideSheet: () -> Unit
) {
    val pagerState = rememberPagerState {
        support.size
    }
    if (showSheet) {
        val currentPage = pagerState.currentPage
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = {
                hideSheet()
            },
            dragHandle = null,
            sheetState = sheetState,
            contentWindowInsets = {
                WindowInsets(0)
            },
        ) {
            val tabs = listOf(Icons.Default.UploadFile to "files", Icons.Default.Mic to "audio recorder").filter {
                support.contains(it.second)
            }
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
            HorizontalPager(pagerState, modifier = Modifier.height(300.dp)) {
                if (tabs[it].second == "files") {
                    val userInfo by LoginViewModel.user.collectAsState()
                    userInfo?.let { it1 -> MediaListView(privateRoomId, it1, clickItem, uploadSuccess) }
                } else {
                    AudioRecorder(privateRoomId, uploadSuccess)
                }
            }
        }
    }
}

@Composable
fun AudioRecorder(privateRoomId: PrimaryKey?, uploadSuccess: (MediaInfo) -> Unit) {
    val isRecording by Recorder.isRecording
    val hazeState = remember { HazeState() }
    val isGranted by isPermissionGranted(Permission.Audio)
    val scope = rememberCoroutineScope()
    val toasterState = rememberToasterState()
    Toaster(toasterState, alignment = Alignment.Center)
    val client = LocalClient.current
    Box(modifier = Modifier.fillMaxSize()) {
        RecorderButton(hazeState, scope, isGranted, isRecording, uploadSuccess, privateRoomId, client)
        if (!isGranted) {
            Box(modifier = Modifier.fillMaxSize().hazeChild(hazeState) {
                backgroundColor = Color.Transparent
            }) {
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
    hazeState: HazeState,
    scope: CoroutineScope,
    isGranted: Boolean,
    isRecording: Boolean,
    uploadSuccess: (MediaInfo) -> Unit,
    privateRoomId: PrimaryKey?,
    client: HttpClient
) {
    Box(
        modifier = Modifier.size(150.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape).align(Alignment.Center)
            .haze(state = hazeState).clickable {
                scope.launch {
                    if (isGranted) {
                        if (isRecording) {
                            val path = Recorder.stopRecord()
                            Napier.i {
                                "save to $path"
                            }
                            uploadPath(privateRoomId, path, client, uploadSuccess)
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
    privateRoomId: PrimaryKey?,
    user: UserInfo,
    clickItem: (MediaInfo) -> Unit,
    uploadSuccess: (MediaInfo) -> Unit
) {
    val client = LocalClient.current
    val list = createMediaListViewModel(privateRoomId, user.id)
    val scope = rememberCoroutineScope()
    val toasterState = rememberToasterState()
    Toaster(toasterState, alignment = Alignment.Center)
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Row {
            IconButton({
                scope.launch {
                    selectFileAndUpload(privateRoomId, client, uploadSuccess)
                }
            }) {
                Icon(Icons.Default.UploadFile, "upload file")
            }
        }
        StateView(list.handler, modifier = Modifier.weight(1f)) {
            LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                items(it.data) {
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        clickItem(it)
                    }) {
                        FileIcon(it)
                        Spacer(modifier = Modifier.width(20.dp))
                        Column {
                            Text(it.item.noPrefixName)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(it.item.lastModified.formatTime())
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun FileIcon(it: MediaInfo) {
    val contentType = it.item.contentType
    val modifier = Modifier.size(40.dp)
    if (contentType.startsWith("image")) {
        AsyncImage(
            it.url,
            it.item.noPrefixName,
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
    privateRoomId: PrimaryKey?,
    client: HttpClient,
    uploadSuccess: (MediaInfo) -> Unit
) {
    val my = LoginViewModel.user.value
    globalDialogState.use {
        val id = my?.id
        val f = FileKit.pickFile()
        if (f != null) {
            upload(
                f.getSize(),
                privateRoomId,
                f.name,
                id,
                ContentType.defaultForFileExtension(f.extension),
                uploadSuccess,
                client
            ) {
                f.readBytes()
            }
        }
    }
}

private suspend fun uploadPath(
    privateRoomId: PrimaryKey?,
    path: Path,
    client: HttpClient,
    uploadSuccess: (MediaInfo) -> Unit
) {
    val my = LoginViewModel.user.value
    val meta = SystemFileSystem.metadataOrNull(path) ?: return
    globalDialogState.use {
        upload(
            meta.size,
            privateRoomId,
            path.name,
            my?.id,
            ContentType.defaultForFilePath(path.toString()),
            uploadSuccess,
            client
        ) {
            SystemFileSystem.source(path).buffered().readByteArray()
        }
    }
}

private suspend fun upload(
    size: Long?,
    privateRoomId: PrimaryKey?,
    name: String,
    id: PrimaryKey?,
    contentType: ContentType,
    uploadSuccess: (MediaInfo) -> Unit,
    client: HttpClient,
    readStream: suspend () -> ByteArray
) {
    if (size != null && size <= 100 * 1024 * 1024) {
        val stream = readStream()
        val response = when {
            privateRoomId != null -> client.upload(
                stream,
                name,
                privateRoomId,
                ObjectType.ROOM,
                contentType
            ).getOrThrow()

            id != null -> client.upload(
                stream,
                name,
                id,
                ObjectType.USER,
                contentType
            ).getOrThrow()

            else -> throw Exception()
        }
        response.data.firstOrNull()?.let {
            bus.emit(OnMediaUploaded(it))
            uploadSuccess(it)
        }
    } else {
        throw Exception("size is null or size too big")
    }
}

fun insertContent(
    it: MediaInfo,
    updateInput: (String) -> Unit,
    input: String
) {
    if (it.item.contentType.startsWith("image/")) {
        updateInput(
            """$input
![${it.item.noPrefixName}](${it.item.noPrefixName} "${it.item.noPrefixName}")
"""
        )
    } else {
        updateInput(
            """$input
```object
{
    "contentType": "${it.item.contentType}",
    "name": "${it.item.noPrefixName}"
}
```"""
        )
    }
}
