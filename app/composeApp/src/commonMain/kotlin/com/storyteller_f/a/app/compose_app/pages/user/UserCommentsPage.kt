package com.storyteller_f.a.app.compose_app.pages.user

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.storyteller_f.a.app.compose_app.common.UserCommentsViewModel
import com.storyteller_f.a.app.compose_app.common.getUserCommentsViewModel
import com.storyteller_f.a.app.compose_app.components.TopicCell
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.pagingItems

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
            modifier = Modifier.padding(top = paddingValues.calculateTopPadding())
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
