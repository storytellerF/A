package com.storyteller_f.a.app.pages.user

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import com.storyteller_f.a.app.common.UserCommentsViewModel
import com.storyteller_f.a.app.common.getUserCommentsViewModel
import com.storyteller_f.a.app.pages.topic.TopicCell
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.app.core.components.safeArea

@Composable
fun UserCommentsPage() {
    val viewModel = getUserCommentsViewModel()
    UserCommentsPageInternal(viewModel)
}

@Composable
fun UserCommentsPageInternal(viewModel: UserCommentsViewModel) {
    Scaffold { paddingValues ->
        StateView(
            viewModel,
            modifier = Modifier.safeArea(paddingValues, LocalLayoutDirection.current)
        ) { items ->
            LazyColumn {
                pagingItems(items, {
                    it.id
                }) {
                    val topicInfo = items[it]
                    if (topicInfo != null) {
                        TopicCell(topicInfo)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
