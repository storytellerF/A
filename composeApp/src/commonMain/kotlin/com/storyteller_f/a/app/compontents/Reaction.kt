package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeMeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.LocalSessionManager
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.model.OnAddReaction
import com.storyteller_f.a.app.model.OnRemoveReaction
import com.storyteller_f.a.app.pages.topic.BaseSheet
import com.storyteller_f.a.app.pages.topic.SheetContainer
import com.storyteller_f.a.app.pages.world.Pill
import com.storyteller_f.a.client_lib.addReaction
import com.storyteller_f.a.client_lib.deleteReaction
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun InteractionRow(
    topicInfo: TopicInfo,
    startAddReaction: () -> Unit,
    startAddComment: () -> Unit
) {
    val userSessionViewModel = LocalSessionManager.current
    val reactions = topicInfo.extension?.reactions
    val appNav = LocalAppNav.current
    InteractionRowInternal(
        reactions.orEmpty(),
        topicInfo,
        startAddComment
    ) {
        if (userSessionViewModel.currentIsAlreadySignUp) {
            startAddReaction()
        } else {
            appNav.gotoLogin()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractionRowInternal(
    data: List<ReactionInfo>,
    topicInfo: TopicInfo,
    startAddComment: () -> Unit,
    startAddReaction: () -> Unit
) {
    val hasComment = topicInfo.hasComment
    val commentCount = topicInfo.commentCount
    var showBottomSheet by remember { mutableStateOf(false) }
    EmojiRow(
        data,
        {
            if (it < data.size) {
                Pill(text = "+${data.size - it}") {
                    showBottomSheet = true
                }
            }
            Pill(icon = Icons.Outlined.AddReaction) {
                startAddReaction()
            }
        },
        { index ->
            if (index == 0) {
                Pill(commentCount.toString(), selected = hasComment, icon = Icons.AutoMirrored.Outlined.Comment) {
                    startAddComment()
                }
            }
            if (data.isNotEmpty()) {
                data.getOrNull(index)?.let { info ->
                    EmojiCell(topicInfo.id, info)
                }
            }
        }
    )
    val sheetState = rememberModalBottomSheetState()
    EmojiSheet(showBottomSheet, sheetState, topicInfo.id, data) {
        showBottomSheet = false
    }
}

@Composable
private fun EmojiRow(
    data: List<ReactionInfo>,
    overflow: @Composable (Int) -> Unit,
    content: @Composable (Int) -> Unit,
) {
    val verticalPx = 4.dp
    val horizontalPx = 8.dp
    SubcomposeLayout { constraints ->
        val maxWidth = constraints.maxWidth
        val firstMeasureResult = measureFirstStage(data, constraints, maxWidth, horizontalPx, content)
        val currentRow = firstMeasureResult.first.last()
        var currentWidth = firstMeasureResult.second
        var emojiUsed = firstMeasureResult.third

        while (true) {
            val overflowPlaceable = subcompose("overflow$emojiUsed") {
                overflow(emojiUsed)
            }.map {
                it.measure(constraints)
            }
            val spacing = horizontalPx.roundToPx() * (overflowPlaceable.size - 1)
            if (currentWidth + overflowPlaceable.sumOf { it.width } + spacing > maxWidth) {
                if (firstMeasureResult.first.size < 2) {
                    // 超过了当前行，但是现在只有一行，选择跳到下一行
                    firstMeasureResult.first.add(mutableListOf<Placeable>().apply {
                        addAll(overflowPlaceable)
                    })
                    break
                } else {
                    // 现在已经有两行了，只能选择删除后面的emoji
                    currentWidth -= currentRow.last().width + horizontalPx.roundToPx()
                    currentRow.removeAt(currentRow.size - 1)
                    emojiUsed--
                }
            } else {
                currentRow.addAll(overflowPlaceable)
                break
            }
        }

        val height = firstMeasureResult.first.sumOf {
            it.maxOf { placeable -> placeable.height }
        } + verticalPx.roundToPx() * (firstMeasureResult.first.size - 1)

        layout(maxWidth, height) {
            var yOffset = 0
            firstMeasureResult.first.forEach { row ->
                var xOffset = 0
                row.forEach {
                    it.placeRelative(xOffset, yOffset)
                    xOffset += it.width + horizontalPx.roundToPx()
                }
                yOffset += row.maxOf { it.height } + verticalPx.roundToPx()
            }
        }
    }
}

private fun SubcomposeMeasureScope.measureFirstStage(
    data: List<ReactionInfo>,
    constraints: Constraints,
    maxWidth: Int,
    horizontalPx: Dp,
    content: @Composable (Int) -> Unit
): Triple<MutableList<MutableList<Placeable>>, Int, Int> {
    val rows = mutableListOf<MutableList<Placeable>>()
    var currentRow = mutableListOf<Placeable>()
    var currentWidth = 0
    var index = 0
    var emojiUsed = 0

    while (index < data.size.coerceAtLeast(1) && rows.size < 2) {
        val placeableList = subcompose("emoji_$index") {
            content(index)
        }.map {
            it.measure(constraints)
        }
        val newWidth = placeableList.sumOf {
            it.width
        } + horizontalPx.roundToPx() * (placeableList.size - 1)

        val nextWidth = if (currentRow.isEmpty()) newWidth else currentWidth + horizontalPx.roundToPx() + newWidth

        when {
            nextWidth <= maxWidth -> {
                currentRow.addAll(placeableList)
                currentWidth = nextWidth
                if (data.isNotEmpty()) {
                    emojiUsed++
                }
                // index++ 会导致退出循环，需要循环后面手动补上最后一行
                index++
            }

            rows.size == 0 -> {
                // 当前还没有完整的一行，并且需要折行
                rows.add(currentRow)
                // 新的一行作为第二行，并且把计算好的placeable 放进list 中，防止重复测量
                index++
                emojiUsed++
                currentRow = mutableListOf<Placeable>().apply {
                    addAll(placeableList)
                }
                currentWidth = placeableList.sumOf {
                    it.width
                } + (horizontalPx.roundToPx() * (placeableList.size - 1))
            }

            else -> {
                // 再加一行就成了第二行，后面会退出循环，清空防止再次加一行
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentWidth = 0
            }
        }
    }

    if (currentRow.isNotEmpty()) {
        rows.add(currentRow)
    }
    val lastRowWidth = rows.last().let {
        it.sumOf {
            it.width
        } + (it.size - 1).coerceAtLeast(0) * horizontalPx.roundToPx()
    }
    return Triple(rows, lastRowWidth, emojiUsed)
}

@Composable
private fun EmojiCell(
    topicId: PrimaryKey,
    info: ReactionInfo
) {
    val scope = rememberCoroutineScope()
    val emoji = info.emoji
    val hasReacted = info.hasReacted
    val sessionManager = LocalSessionManager.current
    Pill(info.count.toString(), emoji = emoji, selected = hasReacted) {
        emoji.let { string ->
            if (hasReacted) {
                scope.launch {
                    globalDialogState.use {
                        sessionManager.deleteReaction(string, topicId)
                        bus.emit(OnRemoveReaction(topicId, string))
                    }
                }
            } else {
                scope.launch {
                    globalDialogState.use {
                        sessionManager.addReaction(topicId, string)
                        bus.emit(OnAddReaction(topicId, string))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiSheet(
    showSheet: Boolean,
    sheetState: SheetState,
    topicId: PrimaryKey,
    list: List<ReactionInfo>,
    hideSheet: () -> Unit
) {
    BaseSheet(showSheet, sheetState, hideSheet) {
        val scrollState = rememberScrollState()
        SheetContainer {
            FlowRow(
                modifier = Modifier.verticalScroll(scrollState).height(300.dp).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                list.forEach {
                    EmojiCell(topicId, it)
                }
            }
        }
    }
}
