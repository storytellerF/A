package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.world.Pill

@Composable
fun ReactionRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Pill("+1", Icons.Outlined.ThumbUp)
        Pill("+1", Icons.Outlined.ThumbUp)
        Pill("+1", Icons.Outlined.ThumbUp)
    }
}
