package com.storyteller_f.a.app.pages.world

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import com.storyteller_f.a.app.common.StateView
import com.storyteller_f.a.app.common.bottomAppending
import com.storyteller_f.a.app.common.topPrepend
import com.storyteller_f.a.app.compontents.TopicCell
import com.storyteller_f.a.app.model.createWorldViewModel
import com.storyteller_f.shared.model.TopicInfo

@Composable
fun WorldPage() {
    val viewModel = createWorldViewModel()
    val items = viewModel.flow.collectAsLazyPagingItems()
    TopicList(items)
}

@Composable
fun TopicList(
    items: LazyPagingItems<TopicInfo>,
    showAvatar: Boolean = true,
    contentAlignAvatar: Boolean = true,
) {
    StateView(items) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            topPrepend(items)
            items(
                count = items.itemCount,
                key = items.itemKey {
                    it.id
                },
            ) {
                items[it]?.let { info -> TopicCell(info, contentAlignAvatar, showAvatar) }
                if (it != items.itemCount - 1) {
                    HorizontalDivider()
                }
            }
            bottomAppending(items)
        }
    }
}

@Composable
fun Pill(
    text: String? = null,
    icon: ImageVector? = null,
    emoji: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val background = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.background(
            background,
            shape
        )
            .height(28.dp)
            .clip(shape)
            .clickable {
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when {
            icon != null -> Icon(
                icon,
                contentDescription = text,
                modifier = Modifier.size(15.dp),
                tint = textColor
            )

            emoji != null -> {
                Text(emoji)
            }
        }
        if (text != null) {
            Text(text, color = textColor, fontSize = 12.sp)
        }
    }
}
