package com.storyteller_f.a.app.user

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.rooms
import a.composeapp.generated.resources.topics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.cash.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.CustomBottomNav
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.NavRoute
import com.storyteller_f.a.app.community.CommunityList
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.a.app.model.createTargetUserJoinedCommunitiesViewModel
import com.storyteller_f.a.app.model.createTopicSearchInUserViewModel
import com.storyteller_f.a.app.model.createUserViewModel
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.app.search.SearchScope
import com.storyteller_f.a.app.world.TopicList
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun UserPage(uid: PrimaryKey) {
    val userViewModel = createUserViewModel(uid)
    val user by userViewModel.handler.data.collectAsState()
    val my by LoginViewModel.user.collectAsState()
    UserPageInternal(user, my, uid)
}

@Composable
private fun UserPageInternal(
    user: UserInfo?,
    my: UserInfo?,
    uid: PrimaryKey
) {
    val pagerState = rememberPagerState {
        2
    }
    val appNav = LocalAppNav.current
    Scaffold(floatingActionButton = {
        if (user != null && my?.id == user.id) {
            FloatingActionButton({
                appNav.gotoTopicCompose(ObjectType.USER, user.id, false, null)
            }) {
                Icon(Icons.Default.Add, "add topic")
            }
        }
    }, bottomBar = {
        val scope = rememberCoroutineScope()
        val navs = listOf(
            NavRoute("/topics", Icons.Default.Topic, stringResource(Res.string.topics)),
            NavRoute("/communities", Icons.Default.Diversity3, stringResource(Res.string.rooms))
        )
        CustomBottomNav(navs[pagerState.currentPage].path, navs) { path ->
            scope.launch {
                pagerState.animateScrollToPage(navs.indexOfFirst {
                    it.path == path
                })
            }
        }
    }) {
        Column(
            modifier = Modifier.padding(bottom = it.calculateBottomPadding()),
        ) {
            CustomSearchBar(SearchScope.UserTopic(uid)) {
                UserIcon(user)
            }
            HorizontalPager(pagerState) {
                if (it == 0) {
                    val topicsViewModel = createTopicSearchInUserViewModel(SearchScope.UserTopic(uid), "")
                    val pagingItems = topicsViewModel.flow.collectAsLazyPagingItems()
                    TopicList(pagingItems)
                } else {
                    val communitiesViewModel = createTargetUserJoinedCommunitiesViewModel(uid)
                    val pagingItems = communitiesViewModel.flow.collectAsLazyPagingItems()
                    CommunityList(pagingItems)
                }
            }
        }
    }
}
