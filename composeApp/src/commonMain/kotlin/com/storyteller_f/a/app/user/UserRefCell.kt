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
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.common.RefCellStateView
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.a.app.model.UserViewModel
import com.storyteller_f.a.app.model.createUserViewModel
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey

@Composable
fun UserRefCell(userId: PrimaryKey) {
    val viewModel = createUserViewModel(userId)

    UserRefCellInternal(viewModel)
}

@Composable
fun UserRefCell(userAid: String) {
    val viewModel = createUserViewModel(userAid)
    UserRefCellInternal(viewModel)
}

@Composable
private fun UserRefCellInternal(viewModel: UserViewModel) {
    val userInfo by viewModel.handler.data.collectAsState()
    val shape = RoundedCornerShape(10.dp)
    val appNav = LocalAppNav.current
    RefCellStateView(
        viewModel.handler,
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, shape)
            .clip(shape)
            .clickable {
                userInfo?.let {
                    appNav.gotoUser(it.id)
                }
            }
            .padding(10.dp)
    ) { info ->
        UserCell(info, true) {
            appNav.gotoUser(it)
        }
    }
}

@Composable
fun UserCell(
    userInfo: UserInfo?,
    customBackground: Boolean,
    avatarSize: Dp = 50.dp,
    onClick: (PrimaryKey) -> Unit = {}
) {
    userInfo ?: return
    Row(
        modifier = if (customBackground) {
            Modifier
                .fillMaxWidth().clickable {
                    onClick(userInfo.id)
                }
        } else {
            Modifier.fillMaxWidth().clickable {
                onClick(userInfo.id)
            }.background(MaterialTheme.colorScheme.surfaceDim, RoundedCornerShape(8.dp))
                .padding(8.dp)
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UserIcon(userInfo, size = avatarSize)
        Column {
            Text(userInfo.nickname)
            val aid = userInfo.aid
            if (aid != null) {
                Text("aid: $aid")
            } else {
                Text("ad: ${userInfo.address}")
            }
        }
    }
}
