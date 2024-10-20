package com.storyteller_f.a.app.world

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.paging.ExperimentalPagingApi
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemContentType
import app.cash.paging.compose.itemKey
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.topic.TopicCell
import com.storyteller_f.a.client_lib.getWorldTopics
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import moe.tlaster.precompose.viewmodel.viewModel

@Composable
fun WorldPage(onClick: (PrimaryKey, ObjectType) -> Unit) {
    val viewModel = viewModel(WorldViewModel::class) {
        WorldViewModel()
    }
    val items = viewModel.flow.collectAsLazyPagingItems()
    TopicList(items, onClick)
}

@OptIn(ExperimentalPagingApi::class)
class WorldViewModel : PagingViewModel<PrimaryKey, TopicInfo>({
    SimplePagingSource {
        serviceCatching {
            client.getWorldTopics(it, 10)
        }.map {
            APagingData(it.data, it.pagination?.nextPageToken?.toULongOrNull())
        }
    }
})

@Composable
fun TopicList(
    items: LazyPagingItems<TopicInfo>,
    onClick: (PrimaryKey, ObjectType) -> Unit
) {
    StateView(items) {
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey(),
                contentType = items.itemContentType()
            ) { index ->
                TopicCell(items[index], onClick = onClick)
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun Pill(text: String, icon: ImageVector) {
    Row(
        modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = text, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(text, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
