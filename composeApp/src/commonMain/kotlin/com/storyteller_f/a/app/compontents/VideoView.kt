package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.storyteller_f.shared.model.MediaInfo
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

const val M3U8_MIMETYPE = "application/vnd.apple.mpegurl"

@OptIn(ExperimentalUuidApi::class)
data class MediaPlayerSession(
    val uuid: Uuid?,
    val id: String,
    val contentType: String,
    val playList: List<PlayItem>,
    val coverMediaInfo: MediaInfo?,
    val videoSize: CustomVideoSize? = null,
)

var savedSession: MutableState<MediaPlayerSession?> = mutableStateOf(null)

@Composable
expect fun VideoView(id: String, contentType: String, playList: List<PlayItem>, coverMediaInfo: MediaInfo?)

@Composable
expect fun rememberIsInPipMode(): Boolean

data class CustomVideoSize(val width: Int, val height: Int)

interface VideoListener {
    fun onPlayStateChange(isPlaying: Boolean)
    fun onUpdateSize(size: CustomVideoSize)
    fun onUpdateLoading(isLoading: Boolean)
}

data class PlayItem(val url: String, val icon: String? = null, val title: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlaylistPicker(
    showSheet: Boolean,
    sheetState: SheetState,
    hideSheet: () -> Unit,
    playList: List<PlayItem>,
    onClick: (PlayItem, Int) -> Unit
) {
    if (showSheet) {
        ModalBottomSheet(
            hideSheet,
            sheetState = sheetState,
            dragHandle = null,
            contentWindowInsets = {
                WindowInsets(0)
            },
        ) {
            LazyColumn(
                modifier = Modifier.height(300.dp).fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(playList.size, key = {
                    playList[it].url
                }) {
                    Row(
                        modifier = Modifier.height(40.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = playList[it].icon
                        if (!icon.isNullOrBlank()) {
                            AsyncImage(icon, "icon", modifier = Modifier.height(30.dp))
                        }
                        Text(playList[it].title ?: playList[it].url, modifier = Modifier.weight(1f))
                        IconButton({
                            onClick(playList[it], it)
                        }) {
                            Icon(Icons.AutoMirrored.Default.PlaylistPlay, "play")
                        }
                    }
                }
            }
        }
    }
}
