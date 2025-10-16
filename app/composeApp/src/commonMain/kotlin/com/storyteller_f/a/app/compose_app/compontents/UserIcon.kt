package com.storyteller_f.a.app.compose_app.compontents

import androidx.compose.runtime.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.compose_app.pages.user.UserDialog
import com.storyteller_f.a.app.core.compontents.UserIconInternal
import com.storyteller_f.shared.model.UserInfo

@Composable
fun UserIcon(
    userInfo: UserInfo?,
    isMe: Boolean = false,
    setClickEvent: Boolean = true,
    size: Dp = 40.dp,
    onClickCreate: () -> Unit = {},
) {
    var showUserDialog by remember {
        mutableStateOf(false)
    }
    val url = userInfo?.avatar?.url
    UserIconInternal(isMe, setClickEvent, url, size = size) {
        showUserDialog = true
    }
    UserDialog(
        isMe,
        userInfo,
        showUserDialog,
        onClickCreate
    ) {
        showUserDialog = false
    }
}
