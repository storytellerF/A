package com.storyteller_f.a.app.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.common.StateView
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.model.createMemberViewModel
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.app.search.SearchScope
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey

@Composable
fun MemberPage(objectId: PrimaryKey, objectType: ObjectType) {
    val viewModel = createMemberViewModel(objectId, objectType)
    val items = viewModel.flow.collectAsLazyPagingItems()

    Scaffold {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CustomSearchBar(
                when (objectType) {
                    ObjectType.COMMUNITY -> SearchScope.CommunityMember(objectId)
                    else -> SearchScope.RoomMember(objectId)
                }
            ) {
            }
            MemberList(items)
        }
    }
}

@Composable
fun MemberList(items: LazyPagingItems<UserInfo>) {
    val appNav = LocalAppNav.current
    StateView(items) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey {
                    it.id
                },
            ) { index ->
                UserCell(items[index], true) {
                    appNav.gotoUser(it)
                }
                Spacer(modifier = Modifier.height(20.dp))
                if (index != items.itemCount - 1) {
                    HorizontalDivider()
                }
            }
        }
    }
}
