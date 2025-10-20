package com.storyteller_f.a.app.compose_app.components

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
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.compose_app.common.TopicsViewModel
import com.storyteller_f.a.app.core.common.PagingViewModel
import com.storyteller_f.a.app.core.compontents.StateView
import com.storyteller_f.a.app.core.compontents.bottomAppending
import com.storyteller_f.a.app.core.compontents.topPrepend
import com.storyteller_f.shared.model.TopicInfo

@Composable
fun TopicList(
    topicsViewModel: PagingViewModel<TopicInfo>,
    showAvatar: Boolean = true,
    supportPin: Boolean = false,
) {
    StateView(topicsViewModel) { items ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            topPrepend(items.loadState)
            items(
                count = items.itemSnapshotList.size,
                key = items.itemKey {
                    it.id
                },
            ) {
                TopicCell(
                    items[it],
                    showAvatar = showAvatar,
                    supportPin
                )
                if (it != items.itemSnapshotList.size - 1) {
                    HorizontalDivider()
                }
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
            items(
                count = items.itemSnapshotList.size,
                key = items.itemKey { topicInfo ->
                    topicInfo.id.toString()
                },
            ) { index ->
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
