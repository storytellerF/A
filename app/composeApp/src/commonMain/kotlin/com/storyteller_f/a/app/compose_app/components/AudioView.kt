package com.storyteller_f.a.app.compose_app.components

import androidx.compose.runtime.Composable
import com.storyteller_f.a.app.core.components.RemoteMediaItem

@Composable
expect fun AudioViewEmbed(remoteMediaItem: RemoteMediaItem)

@Composable
expect fun AudioViewFilled(remoteMediaItem: RemoteMediaItem)

@Composable
expect fun AudioViewFullScreen(remoteMediaItem: RemoteMediaItem)
