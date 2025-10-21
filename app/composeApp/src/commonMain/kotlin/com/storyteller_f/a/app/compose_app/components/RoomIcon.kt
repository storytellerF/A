package com.storyteller_f.a.app.compose_app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.core.compontents.CommonImage
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.utils.safeFirstUnicode

@Composable
fun RoomIcon(
    roomInfo: RoomInfo?,
    size: Dp,
    setClickEvent: Boolean,
    updateDialog: (Boolean) -> Unit
) {
    val iconUrl = roomInfo?.icon?.url
    val radius = 8.dp
    val shape = RoundedCornerShape(radius)
    if (iconUrl != null) {
        CommonImage(
            iconUrl,
            contentDescription = "${roomInfo.name}'s icon",
            modifier = Modifier.size(size).clip(shape).let {
                if (setClickEvent) {
                    it.clickable {
                        updateDialog(true)
                    }
                } else {
                    it
                }
            }
        )
    } else {
        Box(
            modifier = Modifier.size(size)
                .background(MaterialTheme.colorScheme.tertiaryContainer, shape)
                .clip(shape)
                .let {
                    if (setClickEvent) {
                        it.clickable {
                            updateDialog(true)
                        }
                    } else {
                        it
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(roomInfo?.name?.let { safeFirstUnicode(it) } ?: "")
        }
    }
}
