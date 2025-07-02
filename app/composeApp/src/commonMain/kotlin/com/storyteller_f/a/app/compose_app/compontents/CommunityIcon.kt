package com.storyteller_f.a.app.compose_app.compontents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.compose_app.pages.community.CommunityDialog
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.utils.safeFirstUnicode

@Composable
fun CommunityIcon(
    communityInfo: CommunityInfo?,
    showDialog: Boolean,
    iconSize: Dp = 40.dp,
    setClickEvent: Boolean = true,
    onClickIcon: (Boolean) -> Unit,
) {
    val model = communityInfo?.icon?.url
    val shape = RoundedCornerShape(8.dp)
    if (model != null) {
        CommonImage(
            model,
            "icon",
            Modifier.size(iconSize).clip(shape).clickable(setClickEvent) {
                onClickIcon(true)
            }
        )
    } else {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer, shape)
                .clip(shape)
                .size(iconSize)
                .clickable(setClickEvent) {
                    onClickIcon(true)
                },
            contentAlignment = Alignment.Center
        ) {
            CharSequenceText(communityInfo?.name?.let { safeFirstUnicode(it) } ?: "")
        }
    }
    com.storyteller_f.a.app.compose_app.pages.community.CommunityDialog(communityInfo, showDialog) {
        onClickIcon(false)
    }
}

@Composable
fun CommunityPoster(communityInfo: CommunityInfo?) {
    val url = communityInfo?.poster?.url
    val shape = RoundedCornerShape(14.dp)
    if (url != null) {
        CommonImage(
            url,
            contentDescription = "community poster",
            modifier = Modifier.fillMaxSize().clip(shape)
        )
    } else {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, shape)
                .fillMaxSize()
        )
    }
}
