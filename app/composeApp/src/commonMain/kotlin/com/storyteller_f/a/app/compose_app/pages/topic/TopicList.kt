package com.storyteller_f.a.app.compose_app.pages.topic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.storyteller_f.a.app.compose_app.common.TopicsViewModel
import com.storyteller_f.a.app.core.common.PagingViewModel
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.bottomAppending
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.app.core.components.topPrepend
import com.storyteller_f.shared.model.TopicInfo

@Composable
fun TopicList(topicsViewModel: PagingViewModel<TopicInfo>) {
    CommonTopicList(topicsViewModel) { index, info, size ->
        TopicCell(info)
        if (index != size - 1) {
            HorizontalDivider()
        }
    }
}

@Composable
fun UserTopicList(topicsViewModel: PagingViewModel<TopicInfo>) {
    CommonTopicList(topicsViewModel = topicsViewModel) { index, info, size ->
        UserTopicCell(info)
        if (index != size - 1) {
            HorizontalDivider()
        }
    }
}

@Composable
fun CommonTopicList(
    topicsViewModel: PagingViewModel<TopicInfo>,
    block: @Composable (index: Int, info: TopicInfo?, Int) -> Unit,
) {
    StateView(topicsViewModel) { items ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            topPrepend(items.loadState)
            pagingItems(items, {
                it.id
            }) {
                block(it, items[it], items.itemSnapshotList.size)
            }
            bottomAppending(items.loadState)
        }
    }
}

@Composable
fun RoomTopicList(
    items: LazyPagingItems<TopicInfo>,
    topicsViewModel: TopicsViewModel,
    lazyListState: LazyListState,
) {
    StateView(topicsViewModel) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.padding(top = 10.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            reverseLayout = true,
        ) {
            bottomAppending(items.loadState)
            pagingItems(items, {
                it.id
            }) { index ->
                val next = if (index + 1 < items.itemSnapshotList.size) {
                    items[index + 1]
                } else {
                    null
                }
                val info = items[index]
                RoomTopicCell(
                    info,
                    info != null && next?.author != info.author
                )
            }
            topPrepend(items.loadState)
        }
    }
}
