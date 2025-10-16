package com.storyteller_f.a.app.core.compontents

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


@Composable
fun UserIconInternal(
    isMe: Boolean,
    setClickEvent: Boolean,
    avatarUrl: String?,
    size: Dp = 40.dp,
    onClick: () -> Unit
) {
    val modifier = if (isMe) Modifier.testTag("me") else Modifier
    if (avatarUrl != null) {
        CommonImage(
            avatarUrl,
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
