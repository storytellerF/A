package com.storyteller_f.a.panel.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.storyteller_f.a.panel.PanelNav
import com.storyteller_f.a.panel.Res
import com.storyteller_f.a.panel.encrypted
import com.storyteller_f.a.panel.interaction
import com.storyteller_f.a.panel.pinned
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import org.jetbrains.compose.resources.stringResource

@Composable
fun TopicCell(
    info: TopicInfo,
    panelNav: PanelNav
) {
    val text = when (val content = info.content) {
        is TopicContent.Plain -> content.plain
        is TopicContent.Extracted -> content.plain
        else -> ""
    }
    val author = info.extension?.authorInfo?.nickname ?: info.author.toString()
    val room = info.extension?.roomInfo?.name ?: ""
    val overline = listOf(author, room).filter { it.isNotEmpty() }.joinToString(" @ ")
    val counts = if (info.commentCount > 0 || info.reactionCount > 0) {
        stringResource(Res.string.interaction, info.commentCount, info.reactionCount)
    } else {
        ""
    }
    val flags = listOfNotNull(
        if (info.isEncrypted) stringResource(Res.string.encrypted) else null,
        if (info.isPin) stringResource(Res.string.pinned) else null
    ).joinToString(" • ")
    val supporting = listOf(
        info.createdTime.toString(),
        counts,
        flags
    ).filter { it.isNotEmpty() }.joinToString(" • ")

    ListItem(
        modifier = Modifier.clickable { panelNav.gotoTopicDetail(info.id) },
        headlineContent = { Text(text) },
        overlineContent = { Text(overline) },
        supportingContent = {
            Text(supporting)
        },
    )
}
