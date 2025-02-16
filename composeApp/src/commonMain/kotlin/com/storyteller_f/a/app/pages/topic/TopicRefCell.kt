package com.storyteller_f.a.app.pages.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.common.RefCellStateView
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.a.app.model.TopicViewModel
import com.storyteller_f.a.app.model.createTopicViewModel
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.extractMarkdownHeadline

@Composable
fun TopicRefCell(topicId: PrimaryKey) {
    val viewModel = createTopicViewModel(topicId)

    TopicRefCellInternal(viewModel)
}

@Composable
fun TopicRefCell(topicAid: String) {
    val viewModel = createTopicViewModel(topicAid)

    TopicRefCellInternal(viewModel)
}

@Composable
fun TopicRefCellInternal(viewModel: TopicViewModel) {
    val topicInfo by viewModel.handler.data.collectAsState()
    val appNav = LocalAppNav.current
    val shape = RoundedCornerShape(4.dp)
    RefCellStateView(
        viewModel.handler,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .clip(shape)
            .clickable {
                topicInfo?.let { it1 -> appNav.gotoTopic(it1.id) }
            }
            .padding(10.dp),
    ) {
        TopicRefCellContent(it)
    }
}

@Composable
private fun TopicRefCellContent(
    it: TopicInfo,
) {
    val authorInfo = it.extension?.authorInfo
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UserIcon(authorInfo)
        val text = (it.content as? TopicContent.Plain)?.plain.toString()
        val plain = remember {
            extractMarkdownHeadline(text)
        }
        Text(
            plain,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4
        )
    }
}
