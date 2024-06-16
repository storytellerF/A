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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.storyteller_f.a.app.user.UserDialog
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.shared.model.UserInfo

@Composable
fun UserIcon(userInfo: UserInfo?, size: Dp = 40.dp) {
    var showMyDialog by remember {
        mutableStateOf(false)
    }
    val onClick = {
        showMyDialog = true
    }
    val url = userInfo?.avatar?.url
    if (url != null) {
        AsyncImage(
            url,
            contentDescription = "${userInfo.nickname}'s avatar",
            modifier = Modifier.size(size).clip(CircleShape).clickable(onClick = onClick)
        )
    } else {
        Image(
            Icons.Default.AccountCircle,
            contentDescription = "default avatar",
            modifier = Modifier.size(size)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).clickable(onClick = onClick)
                .padding(size / 5)
        )
    }
    UserDialog(userInfo, showMyDialog) {
        showMyDialog = false
    }
}

@Composable
fun MyIcon(size: Dp) {
    val userInfo by LoginViewModel.user.collectAsState()
    UserIcon(userInfo, size)
}
