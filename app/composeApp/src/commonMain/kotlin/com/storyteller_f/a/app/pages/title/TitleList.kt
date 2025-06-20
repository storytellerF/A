package com.storyteller_f.a.app.pages.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.itemKey
import com.storyteller_f.a.app.common.StateView
import com.storyteller_f.a.app.common.bottomAppending
import com.storyteller_f.a.app.common.topPrepend
import com.storyteller_f.a.app.compontents.CommunityIcon
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.a.app.pages.room.RoomIcon
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.type.ObjectType

@Composable
fun TitleList(pagingItems: LazyPagingItems<TitleInfo>) {
    StateView(pagingItems) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            topPrepend(pagingItems)
            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey {
                    it.id.toString()
                },
            ) { index ->
                val title = pagingItems[index]
                title?.let {
                    TitleItem(it)
                }
            }
            bottomAppending(pagingItems)
        }
    }
}

@Composable
fun TitleItem(titleInfo: TitleInfo) {
    Row {
        Text(titleInfo.name)

        titleInfo.extension?.let {
            UserIcon(it.creatorInfo)
            Text("->")
            UserIcon(it.receiverInfo)
            when (titleInfo.type) {
                TitleType.REGULAR -> Text("regular")
                TitleType.JOIN -> Text("join")
            }
            Text("in")
            when (titleInfo.scopeType) {
                ObjectType.COMMUNITY -> CommunityIcon(it.communityInfo, showDialog = false) { }
                ObjectType.ROOM -> RoomIcon(it.roomInfo, showDialog = false) { }
                ObjectType.TOPIC -> TODO()
                ObjectType.USER -> UserIcon(it.receiverInfo)
                ObjectType.TITLE -> TODO()
                ObjectType.MEDIA -> TODO()
            }
        }
    }
}
