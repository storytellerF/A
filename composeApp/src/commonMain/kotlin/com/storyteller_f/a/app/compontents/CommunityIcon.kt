package com.storyteller_f.a.app.compontents

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
import coil3.compose.AsyncImage
import com.storyteller_f.a.app.pages.community.CommunityDialog
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
    val radius = 8.dp
    val shape = RoundedCornerShape(radius)
    if (model != null) {
        AsyncImage(globalLoader(model), contentDescription = null, Modifier.size(iconSize).clip(shape).let {
            if (setClickEvent) {
                it.clickable {
                    onClickIcon(true)
                }
            } else {
                it
            }
        })
    } else {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer, shape)
                .clip(shape)
                .size(iconSize)
                .let {
                    if (setClickEvent) {
                        it.clickable {
                            onClickIcon(true)
                        }
                    } else {
                        it
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            CharSequenceText(communityInfo?.name?.safeFirstUnicode() ?: "")
        }
    }
    CommunityDialog(communityInfo, showDialog) {
        onClickIcon(false)
    }
}

@Composable
fun CommunityPoster(communityInfo: CommunityInfo?) {
    val url = communityInfo?.poster?.url
    val shape = RoundedCornerShape(14.dp)
    if (url != null) {
        AsyncImage(
            globalLoader(url),
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
