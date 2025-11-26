package com.storyteller_f.a.app.pages.title

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.common.TitlesViewModel
import com.storyteller_f.a.app.pages.community.CommunityIconWithDialog
import com.storyteller_f.a.app.pages.room.RoomIconWithDialog
import com.storyteller_f.a.app.pages.user.UserIconWithDialog
import com.storyteller_f.a.app.core.components.LayoutDefaults
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.bottomAppending
import com.storyteller_f.a.app.core.components.topPrepend
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.type.ObjectType

@Composable
fun TitleList(titlesViewModel: TitlesViewModel) {
    StateView(titlesViewModel) { pagingItems ->
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            contentPadding = LayoutDefaults.contentPadding,
            verticalArrangement = LayoutDefaults.pagingVerticalArrangement
        ) {
            topPrepend(pagingItems.loadState)
            items(
                count = pagingItems.itemSnapshotList.size,
                key = pagingItems.itemKey {
                    it.id.toString()
                },
            ) { index ->
                val title = pagingItems[index]
                title?.let {
                    TitleItem(it)
                }
            }
            bottomAppending(pagingItems.loadState)
        }
    }
}

@Composable
fun TitleItem(titleInfo: TitleInfo) {
    Row {
        Text(titleInfo.name)

        titleInfo.extension?.let {
            UserIconWithDialog(it.creatorInfo)
            Text("->")
            UserIconWithDialog(it.receiverInfo)
            when (titleInfo.type) {
                TitleType.REGULAR -> Text("regular")
                TitleType.JOIN -> Text("join")
            }
            Text("in")
            when (titleInfo.scopeType) {
                ObjectType.COMMUNITY -> CommunityIconWithDialog(it.communityInfo, showDialog = false) { }
                ObjectType.ROOM -> RoomIconWithDialog(it.roomInfo, showDialog = false) { }
                ObjectType.TOPIC -> {}
                ObjectType.USER -> UserIconWithDialog(it.receiverInfo)
                ObjectType.TITLE -> {}
                ObjectType.FILE -> {}
                ObjectType.PANEL_ACCOUNT -> {}
            }
        }
    }
}
