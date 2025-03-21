package com.storyteller_f.a.app.compontents

import androidx.compose.runtime.Composable
import com.storyteller_f.shared.model.MediaInfo

@Composable
expect fun AudioView(obj: RemoteMediaItem, coverInfo: MediaInfo?)
