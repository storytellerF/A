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
fun UserIcon(userInfo: UserInfo?, couldShowDialog: Boolean = true) {
    val meInfo = LoginViewModel.user.collectAsState()
    val isMe = meInfo.value?.id == userInfo?.id
    var showMyDialog by remember {
        mutableStateOf(false)
    }
    val url = userInfo?.avatar?.url
    UserIconInternal(isMe, couldShowDialog, url) { ->
        showMyDialog = true
    }
    UserDialog(userInfo, showMyDialog) {
        showMyDialog = false
    }
}

@Composable
fun UserIconInternal(isMe: Boolean, couldShowDialog: Boolean, url: String?, showDialog: () -> Unit) {
    val appNav = LocalAppNav.current
    val showDialog by rememberUpdatedState(showDialog)
    val onClick = {
        if (isMe) {
            appNav.gotoLogin()
        } else if (couldShowDialog) {
            showDialog()
        }
    }
    val size = 40.dp
    val modifier = if (isMe) Modifier.testTag("me") else Modifier
    if (url != null) {
        AsyncImage(
            globalLoader(url),
            contentDescription = "avatar",
            modifier = modifier.size(size).clip(CircleShape).clickable(couldShowDialog, onClick = onClick),
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            Icons.Default.AccountCircle,
            contentDescription = "avatar",
            modifier = modifier.size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .clickable(couldShowDialog, onClick = onClick)
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
