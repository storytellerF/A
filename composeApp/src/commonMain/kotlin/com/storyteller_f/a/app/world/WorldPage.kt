package com.storyteller_f.a.app.world

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
import com.storyteller_f.a.app.model.createWorldViewModel
import com.storyteller_f.a.app.topic.TopicCell
import com.storyteller_f.shared.model.TopicInfo

@Composable
fun WorldPage() {
    val viewModel = createWorldViewModel()
    val items = viewModel.flow.collectAsLazyPagingItems()
    TopicList(items)
}

@Composable
fun TopicList(
    items: LazyPagingItems<TopicInfo>
) {
    StateView(items) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey {
                    it.id
                },
            ) {
                items[it]?.let { info -> TopicCell(info) }
                Spacer(modifier = Modifier.height(20.dp))
                if (it != items.itemCount - 1) {
                    HorizontalDivider()
                }
            }
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
    Row(
        modifier = Modifier.background(
            when {
                selected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
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
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )

            emoji != null -> {
                Text(emoji)
            }
        }
        if (text != null) {
            Text(text, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 12.sp)
        }
    }
}
