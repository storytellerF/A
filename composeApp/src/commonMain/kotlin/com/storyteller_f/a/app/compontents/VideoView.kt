package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.storyteller_f.a.app.MediaPlaySession
import com.storyteller_f.shared.model.MediaInfo
import kotlinx.serialization.Serializable

const val M3U8_MIMETYPE = "application/vnd.apple.mpegurl"
const val YOUTUBE_MIMETYPE = "video/youtube"
const val SOUND_CLOUD_MIME_TYPE = "audio/sound.cloud"

val playerSession: MutableState<MediaPlaySession.VideoOrAudio?> = mutableStateOf(null)

@Serializable
data class RemoteMediaItem(
    val url: String,
    val contentType: String,
    val userInputContentType: String,
    val isM3U8PlayList: Boolean,
    val name: String,
    val coverMediaInfo: MediaInfo? = null,
    val title: String? = null
)

@Composable
expect fun VideoView(
    obj: RemoteMediaItem,
    isEmbed: Boolean
)

@Composable
expect fun rememberIsInPipMode(): Boolean

@Serializable
data class CustomVideoSize(val width: Int, val height: Int)

interface VideoListener {
    fun onPlayStateChange(isPlaying: Boolean)
    fun onUpdateSize(size: CustomVideoSize)
    fun onUpdateLoading(isLoading: Boolean)
    fun onMediaItemChanged(mediaId: String?, currentMediaItemIndex: Int)
}

interface PlayItem {
    val id: String
    val icon: String?
    val title: String?
}

@Serializable
data class ConstPlayItem(
    val url: String,
    override val icon: String? = null,
    override val title: String? = null
) : PlayItem {
    override val id: String
        get() = url
}

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
                    playList[it].id
                }) {
                    Row(
                        modifier = Modifier.height(40.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = playList[it].icon
                        if (!icon.isNullOrBlank()) {
                            AsyncImage(icon, "icon", modifier = Modifier.height(30.dp))
                        } else {
                            Box(modifier = Modifier.height(30.dp))
                        }
                        Text(
                            playList[it].title ?: "unknown",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall
                        )
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
