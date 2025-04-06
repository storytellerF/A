package com.storyteller_f.a.app.pages.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.common.RefCellStateView
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.a.app.model.UserViewModel
import com.storyteller_f.a.app.model.createUserViewModel
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey

@Composable
fun UserRefCell(userId: PrimaryKey, onClick: ((UserInfo) -> Unit)? = null) {
    val viewModel = createUserViewModel(userId)
    UserRefCellInternal(viewModel, onClick)
}

@Composable
fun UserRefCell(userAid: String, onClick: ((UserInfo) -> Unit)? = null) {
    val viewModel = createUserViewModel(userAid)
    UserRefCellInternal(viewModel, onClick)
}

@Composable
private fun UserRefCellInternal(
    viewModel: UserViewModel,
    onClick: ((UserInfo) -> Unit)? = null
) {
    val userInfo by viewModel.handler.data.collectAsState()
    val shape = RoundedCornerShape(10.dp)
    val appNav = LocalAppNav.current
    RefCellStateView(
        viewModel.handler,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, shape)
            .clip(shape)
            .clickable {
                userInfo?.let {
                    appNav.gotoUser(it.id)
                }
            }
    ) { info ->
        UserCell(info, hideBackground = true, onClickCell = onClick)
    }
}

@Composable
fun UserCell(
    userInfo: UserInfo?,
    hideBackground: Boolean,
    clickable: Boolean = true,
    cellClickable: Boolean = true,
    size: Dp = 40.dp,
    onClickCell: ((UserInfo) -> Unit)? = null
) {
    val shape = RoundedCornerShape(8.dp)
    val appNav = LocalAppNav.current
    Row(
        modifier = if (hideBackground) {
            Modifier
                .fillMaxWidth().clip(shape).clickable(userInfo != null && cellClickable) {
                    userInfo?.let {
                        onClickCell?.invoke(it) ?: appNav.gotoUser(it.id)
                    }
                }
        } else {
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceDim, shape)
                .clip(shape)
                .clickable(userInfo != null && cellClickable) {
                    userInfo?.let {
                        onClickCell?.invoke(it) ?: appNav.gotoUser(it.id)
                    }
                }
        }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UserIcon(userInfo, setClickEvent = clickable, size = size)
        if (userInfo != null) {
            Column {
                Text(userInfo.nickname, style = MaterialTheme.typography.titleMedium)
                val aid = userInfo.aid
                if (aid != null) {
                    Text("aid: $aid", style = MaterialTheme.typography.labelSmall)
                } else {
                    Text("ad: ${userInfo.address}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
