package com.storyteller_f.a.app.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.common.RefCellStateView
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey

@Composable
fun UserRefCell(userId: PrimaryKey, onClick: (PrimaryKey) -> Unit) {
    val viewModel = viewModel(UserViewModel::class, keys = listOf("user", userId)) {
        UserViewModel(userId)
    }

    UserRefCellInternal(viewModel, onClick)
}

@Composable
fun UserRefCell(userAid: String, onClick: (PrimaryKey) -> Unit) {
    val viewModel = viewModel(UserViewModel::class, keys = listOf("user", userAid)) {
        UserViewModel(userAid)
    }

    UserRefCellInternal(viewModel, onClick)
}

@Composable
private fun UserRefCellInternal(viewModel: UserViewModel, onClick: (PrimaryKey) -> Unit) {
    val userInfo by viewModel.handler.data.collectAsState()
    val shape = RoundedCornerShape(10.dp)
    RefCellStateView(
        viewModel.handler,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, shape)
            .clip(shape)
            .clickable {
                userInfo?.let {
                    onClick(it.id)
                }
            }
            .padding(10.dp)
    ) {
        UserRow(it)
    }
}

@Composable
fun UserRow(userInfo: UserInfo?, avatarSize: Dp = 40.dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UserIcon(userInfo, avatarSize)
        Column {
            userInfo?.let { Text(it.nickname) }
        }
    }
}
