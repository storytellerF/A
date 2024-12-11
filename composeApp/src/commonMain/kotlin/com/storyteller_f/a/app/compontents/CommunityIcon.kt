package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.storyteller_f.a.app.community.CommunityDialog
import com.storyteller_f.a.app.utils.safeFirstUnicode
import com.storyteller_f.shared.model.CommunityInfo

@Composable
fun CommunityIcon(
    communityInfo: CommunityInfo?,
    iconSize: Dp = 40.dp,
    showDialog: Boolean,
    updateDialog: (Boolean) -> Unit,
) {
    val model = communityInfo?.icon?.url
    val radius = 8.dp
    val shape = RoundedCornerShape(radius)
    if (model != null) {
        AsyncImage(model, contentDescription = null, Modifier.size(iconSize).clip(shape).clickable {
            updateDialog(true)
        })
    } else {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer, shape)
                .clip(shape)
                .size(iconSize)
                .clickable {
                    updateDialog(true)
                },
            contentAlignment = Alignment.Center
        ) {
            CharSequenceText(communityInfo?.name?.safeFirstUnicode() ?: "")
        }
    }
    CommunityDialog(communityInfo, showDialog, {
        updateDialog(false)
    })
}
