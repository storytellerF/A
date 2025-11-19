package com.storyteller_f.a.app.core.components

import androidx.compose.runtime.Composable

@Composable
expect fun AudioViewEmbed(remoteMediaItem: RemoteMediaItem)

@Composable
expect fun AudioViewFilled(remoteMediaItem: RemoteMediaItem)

@Composable
expect fun AudioViewFullScreen(remoteMediaItem: RemoteMediaItem)
