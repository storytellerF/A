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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.pages.user.UserDialog
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.shared.model.UserInfo

@Composable
fun UserIcon(userInfo: UserInfo?, couldShowDialog: Boolean = true) {
    val appNav = LocalAppNav.current
    val user = LoginViewModel.user.collectAsState()
    val isMe = user.value?.id == userInfo?.id
    var showMyDialog by remember {
        mutableStateOf(false)
    }
    val size = 40.dp
    val onClick = {
        if (isMe && userInfo == null) {
            appNav.gotoLogin()
        } else if (couldShowDialog) {
            showMyDialog = true
        }
    }
    val url = userInfo?.avatar?.url
    if (url != null) {
        AsyncImage(
            globalLoader(url),
            contentDescription = "${userInfo.nickname}'s avatar",
            modifier = Modifier.size(size).clip(CircleShape).clickable(couldShowDialog, onClick = onClick),
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            Icons.Default.AccountCircle,
            contentDescription = "default avatar",
            modifier = Modifier.size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .clickable(couldShowDialog, onClick = onClick)
                .padding(size / 5)
        )
    }
    UserDialog(userInfo, showMyDialog) {
        showMyDialog = false
    }
}

@Composable
fun globalLoader(url: String) =
    ImageRequest.Builder(LocalPlatformContext.current).data(url).crossfade(true).fetcherFactory(
        KtorNetworkFetcherFactory(client)
    ).build()
