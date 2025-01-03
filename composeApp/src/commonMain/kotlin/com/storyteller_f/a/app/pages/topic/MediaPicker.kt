package com.storyteller_f.a.app.pages.topic

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dokar.sonner.Toaster
import com.dokar.sonner.ToasterState
import com.dokar.sonner.rememberToasterState
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.client
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.extension
import io.github.vinceglb.filekit.core.pickFile
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPicker(
    showSheet: Boolean,
    sheetState: SheetState,
    input: String,
    updateInput: (String) -> Unit,
    privateRoomId: PrimaryKey?,
    user: UserInfo,
    hideSheet: () -> Unit
) {
    if (showSheet) {
        val pagerState = rememberPagerState {
            2
        }
        val currentPage = pagerState.currentPage
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
            val tabs = listOf(Icons.Default.UploadFile to "files", Icons.Default.Mic to "audio recorder")
            PrimaryTabRow(currentPage) {
                tabs.forEachIndexed { index, pair ->
                    Tab(currentPage == index, {
                    }) {
                        Icon(pair.first, pair.second)
                    }
                }
            }
            HorizontalPager(pagerState, modifier = Modifier.height(300.dp)) {
                if (it == 0) {
                    MediaListView(input, privateRoomId, user, updateInput)
                } else {
                    AudioRecorder(privateRoomId)
                }
            }
        }
    }
}

@Composable
fun AudioRecorder(privateRoomId: PrimaryKey?) {
    val isRecording by Recorder.isRecording
    val hazeState = remember { HazeState() }
    val isGranted by isPermissionGranted(Permission.Audio)
    val scope = rememberCoroutineScope()
    val toasterState = rememberToasterState()
    val my by LoginViewModel.user.collectAsState()
    Toaster(toasterState)
    Box(modifier = Modifier.fillMaxSize()) {
        IconButton({
            scope.launch {
                if (isGranted) {
                    if (isRecording) {
                        val path = Recorder.stopRecord()
                        Napier.i {
                            "save to $path"
                        }
                        upload(my, toasterState, privateRoomId, path)
                    } else {
                        Recorder.startRecord()
                    }
                }
            }
        }, modifier = Modifier.align(Alignment.Center).haze(state = hazeState)) {
            if (isRecording) {
                Image(Icons.Default.StopCircle, "stop record", modifier = Modifier.size(200.dp))
            } else {
                Image(Icons.Default.PlayCircle, "start record", modifier = Modifier.size(200.dp))
            }
        }

        val surface = MaterialTheme.colorScheme.surface
        if (!isGranted) {
            Box(modifier = Modifier.fillMaxSize().hazeChild(hazeState) {
                this.backgroundColor = surface
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
private fun MediaListView(
    input: String,
    privateRoomId: PrimaryKey?,
    user: UserInfo,
    updateInput: (String) -> Unit
) {
    val list = createMediaListViewModel(privateRoomId, user.id)
    val scope = rememberCoroutineScope()
    val toasterState = rememberToasterState()
    val my by LoginViewModel.user.collectAsState()
    Toaster(toasterState)
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Row {
            IconButton({
                scope.launch {
                    upload(my, toasterState, privateRoomId)
                }
            }) {
                Icon(Icons.Default.UploadFile, "upload file")
            }
            IconButton({
                list.handler.refresh()
            }) {
                Icon(Icons.Default.Refresh, "refresh file")
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            StateView(list.handler) {
                LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                    items(it.data) {
                        Row(modifier = Modifier.clickable {
                            insertContent(it, updateInput, input)
                            toasterState.show("success", duration = 1.seconds)
                        }) {
                            AsyncImage(
                                it.url,
                                it.item.noPrefixName,
                                modifier = Modifier.size(40.dp),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(20.dp))
                            Column {
                                Text(it.item.noPrefixName)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(it.item.lastModified.toString())
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun upload(
    my: UserInfo?,
    toasterState: ToasterState,
    privateRoomId: PrimaryKey?
) {
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
                toasterState
            ) {
                f.readBytes()
            }
        }
    }
}

private suspend fun upload(
    my: UserInfo?,
    toasterState: ToasterState,
    privateRoomId: PrimaryKey?,
    path: Path
) {
    val meta = SystemFileSystem.metadataOrNull(path) ?: return
    globalDialogState.use {
        upload(
            meta.size,
            privateRoomId,
            path.name,
            my?.id,
            ContentType.parse("audio/mp4"),
            toasterState
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
    toasterState: ToasterState,
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
        }
    } else {
        toasterState.show("size is null or size too big", duration = 1.seconds)
    }
}

private fun insertContent(
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
