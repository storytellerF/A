package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.ExperimentalPagingApi
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.world.Pill
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch

data class OnTopicChanged(val topicInfo: TopicInfo)

@OptIn(ExperimentalPagingApi::class)
class ReactionsViewModel(val objectId: PrimaryKey) : SimpleViewModel<ServerResponse<ReactionInfo>>() {
    init {
        load()
    }

    override suspend fun loadInternal() {
        handler.request {
            runCatching {
                client.getReactions(objectId)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractionRow(
    topicInfo: TopicInfo,
    startAddReaction: () -> Unit,
    startAddComment: () -> Unit
) {
    val objectId = topicInfo.id
    val commentCount = topicInfo.commentCount
    val hasComment = topicInfo.hasComment
    val reactionCount = topicInfo.reactionCount
    val reactionsViewModel = viewModel(ReactionsViewModel::class, keys = listOf("reactions", objectId)) {
        ReactionsViewModel(objectId)
    }
    val reactions by reactionsViewModel.handler.data.collectAsState()
    val refresh by reactionsViewModel.handler.state.collectAsState()
    val itemCount = reactions?.data?.size ?: 0
    LaunchedEffect(refresh, itemCount, reactionCount) {
        if (refresh != null && refresh is LoadingState.Done && itemCount.toLong() != reactionCount) {
            reactionsViewModel.handler.refresh()
        }
    }
    var maxLines by remember {
        mutableStateOf(2)
    }

    val it = reactions ?: ServerResponse(emptyList())
    val moreOrCollapseIndicator = @Composable { scope: ContextualFlowRowOverflowScope ->
        InteractionRowEnd(scope, startAddReaction, maxLines, {
            maxLines = 2
        }) {
            maxLines = Int.MAX_VALUE
        }
    }
    val data = it.data
    ContextualFlowRow(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        maxLines = maxLines,
        overflow = ContextualFlowRowOverflow.expandOrCollapseIndicator(
            minRowsToShowCollapse = 1,
            expandIndicator = moreOrCollapseIndicator,
            collapseIndicator = moreOrCollapseIndicator
        ),
        itemCount = data.size.coerceAtLeast(1)
    ) { index ->
        if (index == 0) {
            Pill(commentCount.toString(), selected = hasComment, icon = Icons.AutoMirrored.Outlined.Comment) {
                startAddComment()
            }
        }
        if (data.isNotEmpty()) {
            data.getOrNull(index)?.let { info ->
                EmojiCell(topicInfo, reactionCount, info)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun InteractionRowEnd(
    scope: ContextualFlowRowOverflowScope,
    startAddReaction: () -> Unit,
    maxLines: Int,
    shrink: () -> Unit,
    expand: () -> Unit,
) {
    val appNav = LocalAppNav.current
    val remainingItems = scope.totalItemCount - scope.shownItemCount
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(icon = Icons.Outlined.AddReaction) {
            if (LoginViewModel.currentIsAlreadySignUp) {
                startAddReaction()
            } else {
                appNav.gotoLogin()
            }
        }
        if (remainingItems > 0) {
            Pill(text = "+$remainingItems") {
                expand()
            }
        } else if (maxLines > 2) {
            Pill(icon = Icons.Default.Close) {
                shrink()
            }
        }
    }
}

@Composable
private fun EmojiCell(
    topicInfo: TopicInfo,
    reactionCount: Long,
    info: ReactionInfo
) {
    val scope = rememberCoroutineScope()
    val first = info
    val emoji = first.emoji
    val hasReacted = first.hasReacted
    Pill(first.count.toString(), emoji = emoji, selected = hasReacted == true) {
        emoji.let { string ->
            if (hasReacted == true) {
                scope.launch {
                    globalDialogState.use {
                        client.deleteReaction(string)
                        bus.emit(OnTopicChanged(topicInfo.copy(reactionCount = reactionCount - 1)))
                    }
                }
            } else {
                scope.launch {
                    globalDialogState.use {
                        client.addReaction(topicInfo.id, string)
                        bus.emit(OnTopicChanged(topicInfo.copy(reactionCount = reactionCount + 1)))
                    }
                }
            }
        }
    }
}
