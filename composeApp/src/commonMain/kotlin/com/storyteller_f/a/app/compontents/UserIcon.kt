package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.LocalClient
import com.storyteller_f.a.app.pages.user.UserDialog
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.shared.model.UserInfo

@Composable
fun UserIcon(
    userInfo: UserInfo?,
    isMe: Boolean = false,
    setClickEvent: Boolean = true,
    size: Dp = 40.dp,
    clickCreate: () -> Unit = {},
) {
    var showUserDialog by remember {
        mutableStateOf(false)
    }
    val url = userInfo?.avatar?.url
    val appNav = LocalAppNav.current
    val me by LoginViewModel.user.collectAsState()
    val onClick = {
        when {
            isMe && me == null -> appNav.gotoLogin()
            else -> showUserDialog = true
        }
    }
    UserIconInternal(url, isMe, setClickEvent, size = size, onClick = onClick)
    UserDialog(userInfo, showUserDialog, clickCreate) {
        showUserDialog = false
    }
}

@Composable
fun UserIconInternal(
    avatarUrl: String?,
    isMe: Boolean,
    setClickEvent: Boolean,
    size: Dp = 40.dp,
    onClick: () -> Unit
) {
    val modifier = if (isMe) Modifier.testTag("me") else Modifier
    if (avatarUrl != null) {
        AsyncImage(
            globalLoader(avatarUrl),
            contentDescription = "avatar",
            modifier = modifier.size(size).clip(CircleShape).let {
                if (setClickEvent) {
                    it.clickable(onClick = onClick)
                } else {
                    it
                }
            },
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            Icons.Default.AccountCircle,
            contentDescription = "avatar",
            modifier = modifier.size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .let {
                    if (setClickEvent) {
                        it.clickable(onClick = onClick)
                    } else {
                        it
                    }
                }
                .padding(size / 5)
        )
    }
}

@Composable
fun globalLoader(url: String): ImageRequest {
    val client = LocalClient.current
    val platformContext = LocalPlatformContext.current
    return remember(url) {
        ImageRequest.Builder(platformContext).data(url).crossfade(true).fetcherFactory(
            KtorNetworkFetcherFactory(client)
        ).build()
    }
}
