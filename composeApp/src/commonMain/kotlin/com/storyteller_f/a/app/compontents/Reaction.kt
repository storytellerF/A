package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.*
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.model.OnAddReaction
import com.storyteller_f.a.app.model.OnRemoveReaction
import com.storyteller_f.a.app.model.OnTopicChanged
import com.storyteller_f.a.app.model.createReactionsViewModel
import com.storyteller_f.a.app.pages.world.Pill
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ServerResponse
import kotlinx.coroutines.launch

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
    val reactionsViewModel = createReactionsViewModel(objectId)
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
    val emoji = info.emoji
    val hasReacted = info.hasReacted
    Pill(info.count.toString(), emoji = emoji, selected = hasReacted) {
        emoji.let { string ->
            if (hasReacted) {
                scope.launch {
                    globalDialogState.use {
                        client.deleteReaction(string, topicInfo.id)
                        bus.emit(OnTopicChanged(topicInfo.copy(reactionCount = reactionCount - 1)))
                        bus.emit(OnRemoveReaction(topicInfo.id, string))
                    }
                }
            } else {
                scope.launch {
                    globalDialogState.use {
                        client.addReaction(topicInfo.id, string)
                        bus.emit(OnTopicChanged(topicInfo.copy(reactionCount = reactionCount + 1)))
                        bus.emit(OnAddReaction(topicInfo.id, string))
                    }
                }
            }
        }
    }
}
