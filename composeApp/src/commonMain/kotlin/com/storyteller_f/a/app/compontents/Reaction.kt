package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import app.cash.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.world.Pill
import com.storyteller_f.a.client_lib.addReaction
import com.storyteller_f.a.client_lib.deleteReaction
import com.storyteller_f.a.client_lib.getReactions
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class OnTopicChanged(val topicInfo: TopicInfo)

@OptIn(ExperimentalPagingApi::class)
class ReactionsViewModel(val objectId: PrimaryKey) : PagingViewModel<String, Pair<ReactionInfo, Int>>({
    SimplePagingSource {
        serviceCatching {
            val reactions = client.getReactions(objectId)
            reactions
            ServerResponse(reactions.data.mapIndexed { index, info ->
                info to index
            })
        }.map {
            APagingData(it.data, it.pagination?.nextPageToken)
        }
    }
})

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
    val reactions = reactionsViewModel.flow.collectAsLazyPagingItems()
    val refresh = reactions.loadState.refresh
    val itemCount = reactions.itemCount
    LaunchedEffect(refresh, itemCount, reactionCount) {
        if (refresh is LoadState.NotLoading && itemCount.toLong() != reactionCount) {
            reactions.refresh()
        }
    }
    var maxLines by remember {
        mutableStateOf(2)
    }

    val moreOrCollapseIndicator = @Composable { scope: ContextualFlowRowOverflowScope ->
        val remainingItems = scope.totalItemCount - scope.shownItemCount
        if (remainingItems > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(icon = Icons.Outlined.AddReaction) {
                    startAddReaction()
                }
                Pill(text = "+$remainingItems") {}
            }
        } else {
            Pill(icon = Icons.Outlined.AddReaction) {
                startAddReaction()
            }
        }
    }
    val scope = rememberCoroutineScope()

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
        itemCount = reactions.itemCount.coerceAtLeast(1)
    ) { index ->
        if (index == 0) {
            Pill(commentCount.toString(), selected = hasComment, icon = Icons.AutoMirrored.Outlined.Comment) {
                startAddComment()
            }
        }
        if (reactions.itemCount > 0) {
            EmojiCell(scope, topicInfo, reactionCount, reactions[index])
        }
    }
}

@Composable
private fun EmojiCell(
    scope: CoroutineScope,
    topicInfo: TopicInfo,
    reactionCount: Long,
    info: Pair<ReactionInfo, Int>?
) {
    val first = info?.first
    val emoji = first?.emoji
    val hasReacted = first?.hasReacted
    Pill((first?.count ?: 0).toString(), emoji = emoji, selected = hasReacted == true) {
        emoji?.let { string ->
            if (hasReacted == true) {
                scope.launch {
                    globalDialogState.use {
                        client.deleteReaction(string)
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
