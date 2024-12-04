package com.storyteller_f.a.app.world

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.ExperimentalPagingApi
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemContentType
import app.cash.paging.compose.itemKey
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.topic.TopicCell
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.getWorldTopics
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import org.kodein.emoji.compose.m3.TextWithNotoAnimatedEmoji

@Composable
fun WorldPage() {
    val viewModel = viewModel(WorldViewModel::class) {
        WorldViewModel()
    }
    val items = viewModel.flow.collectAsLazyPagingItems()
    TopicList(items)
}

@OptIn(ExperimentalPagingApi::class)
class WorldViewModel : PagingViewModel<PrimaryKey, TopicInfo>({
    SimplePagingSource {
        serviceCatching {
            client.getWorldTopics(it, 10, LoginViewModel.isAlreadySignUp)
        }.map {
            APagingData(it.data, it.pagination?.nextPageToken?.toPrimaryKeyOrNull())
        }
    }
})

@Composable
fun TopicList(
    items: LazyPagingItems<TopicInfo>
) {
    StateView(items) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey(),
                contentType = items.itemContentType()
            ) { index ->
                items[index]?.let { TopicCell(it) }
                Spacer(modifier = Modifier.height(20.dp))
                if (index != items.itemCount - 1) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun Pill(
    text: String? = null,
    icon: ImageVector? = null,
    emoji: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier.background(
            when {
                selected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            shape
        )
            .height(28.dp)
            .clip(shape)
            .clickable {
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when {
            icon != null -> Icon(
                icon,
                contentDescription = text,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )

            emoji != null -> Text(emoji)
        }
        if (text != null)
            Text(text, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 12.sp)
    }
}
