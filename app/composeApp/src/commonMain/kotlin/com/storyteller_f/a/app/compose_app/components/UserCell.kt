package com.storyteller_f.a.app.compose_app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.compose_app.pages.user.UserIconWithDialog
import com.storyteller_f.shared.model.UserInfo

@Composable
fun UserCell(
    userInfo: UserInfo?,
    iconClickable: Boolean = true,
    cellClickable: Boolean = true,
    iconSize: Dp = 40.dp,
    onClickCell: (UserInfo) -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val modifier = Modifier.fillMaxWidth()
        .clip(shape)
        .clickable(userInfo != null && cellClickable) {
            userInfo?.let {
                onClickCell.invoke(it)
            }
        }.padding(8.dp)
    UserCellInternal(modifier, userInfo, iconClickable, iconSize)
}

@Composable
fun UnboundedUserCell(
    userInfo: UserInfo?,
    iconClickable: Boolean = true,
    iconSize: Dp = 40.dp
) = UserCellInternal(Modifier.fillMaxWidth().padding(8.dp), userInfo, iconClickable, iconSize)

@Composable
private fun UserCellInternal(
    modifier: Modifier,
    userInfo: UserInfo?,
    iconClickable: Boolean,
    iconSize: Dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UserIconWithDialog(
            userInfo,
            setClickEvent = iconClickable,
            size = iconSize
        )
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
