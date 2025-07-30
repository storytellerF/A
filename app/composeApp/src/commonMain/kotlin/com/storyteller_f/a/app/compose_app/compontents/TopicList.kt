package com.storyteller_f.a.app.compose_app.compontents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.compose_app.common.StateView
import com.storyteller_f.a.app.compose_app.common.bottomAppending
import com.storyteller_f.a.app.compose_app.common.topPrepend
import com.storyteller_f.shared.model.TopicInfo


@Composable
fun TopicList(
    items: LazyPagingItems<TopicInfo>,
    showAvatar: Boolean = true,
) {
    StateView(items) {
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
                    showAvatar = showAvatar
                )
                if (it != items.itemSnapshotList.size - 1) {
                    HorizontalDivider()
                }
            }
            bottomAppending(items.loadState)
        }
    }
}
