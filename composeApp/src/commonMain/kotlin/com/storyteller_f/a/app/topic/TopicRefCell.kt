package com.storyteller_f.a.app.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.common.RefCellStateView
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.user.UserViewModel
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.PrimaryKey

@Composable
fun TopicRefCell(topicId: PrimaryKey, onClick: (PrimaryKey) -> Unit) {
    val viewModel = viewModel(keys = listOf("topic", topicId)) {
        TopicViewModel(topicId)
    }

    TopicRefCellInternal(viewModel, onClick)
}

@Composable
fun TopicRefCell(topicAid: String, onClick: (PrimaryKey) -> Unit) {
    val viewModel = viewModel(keys = listOf("topic", topicAid)) {
        TopicViewModel(topicAid)
    }

    TopicRefCellInternal(viewModel, onClick)
}

@Composable
fun TopicRefCellInternal(viewModel: TopicViewModel, onClick: (PrimaryKey) -> Unit) {
    val it by viewModel.handler.data.collectAsState()
    val shape = RoundedCornerShape(4.dp)
    RefCellStateView(
        viewModel.handler,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .clip(shape)
            .clickable {
                it?.let { it1 -> onClick(it1.id) }
            }
            .padding(10.dp),
    ) {
        TopicRefCellContent(it, onClick)
    }
}

@Composable
private fun TopicRefCellContent(
    it: TopicInfo,
    onClick: (PrimaryKey) -> Unit,
) {
    val author = it.author
    val authorViewModel = viewModel(keys = listOf("user", author)) {
        UserViewModel(author)
    }
    val authorInfo by authorViewModel.handler.data.collectAsState()
    Row(
        modifier = Modifier.clickable {
            onClick(it.id)
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        authorInfo?.let {
            Text("${authorInfo?.nickname} :")
        }
        Text(
            (it.content as? TopicContent.Plain)?.plain.toString(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3
        )
    }
}
