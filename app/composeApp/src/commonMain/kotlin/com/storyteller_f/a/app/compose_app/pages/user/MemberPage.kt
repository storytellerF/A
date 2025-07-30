package com.storyteller_f.a.app.compose_app.pages.user

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.compose_app.common.StateView
import com.storyteller_f.a.app.compose_app.common.bottomAppending
import com.storyteller_f.a.app.compose_app.common.topPrepend
import com.storyteller_f.a.app.compose_app.model.createMemberViewModel
import com.storyteller_f.a.app.compose_app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.compose_app.pages.search.SearchScope
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey

@Composable
fun MemberPage(objectId: PrimaryKey, objectType: ObjectType) {
    Scaffold {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CustomSearchBar(
                when (objectType) {
                    ObjectType.COMMUNITY -> SearchScope.CommunityMember(
                        objectId
                    )

                    else -> SearchScope.RoomMember(
                        objectId
                    )
                }
            ) {
            }
            val viewModel =
                createMemberViewModel(objectId, objectType)
            MemberList(viewModel.flow.collectAsLazyPagingItems())
        }
    }
}

@Composable
fun MemberList(items: LazyPagingItems<UserInfo>, onClick: ((UserInfo) -> Unit)? = null) {
    val debounced = items.loadState
    StateView(items) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            topPrepend(debounced)
            items(
                count = items.itemSnapshotList.size,
                key = items.itemKey {
                    it.id
                },
            ) { index ->
                UserCell(items[index], true, onClickCell = onClick)
                Spacer(modifier = Modifier.height(20.dp))
                if (index != items.itemSnapshotList.size - 1) {
                    HorizontalDivider()
                }
            }
            bottomAppending(debounced)
        }
    }
}
