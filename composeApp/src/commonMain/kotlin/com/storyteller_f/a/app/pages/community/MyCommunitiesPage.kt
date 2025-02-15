package com.storyteller_f.a.app.pages.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.compontents.CommunityIcon
import com.storyteller_f.a.app.compontents.rememberCommonDialogController
import com.storyteller_f.a.app.model.createJoinedCommunitiesViewModel
import com.storyteller_f.a.app.utils.lcm
import com.storyteller_f.shared.model.CommunityInfo

@Composable
fun MyCommunitiesPage() {
    val viewModel = createJoinedCommunitiesViewModel()
    val items = viewModel.flow.collectAsLazyPagingItems()
    CommunityList(items)
}

@Composable
fun CommunityList(items: LazyPagingItems<CommunityInfo>, onClick: ((CommunityInfo) -> Unit)? = null) {
    StateView(items, modifier = Modifier.fillMaxSize()) {
        CommunityConstrains(modifier = Modifier.fillMaxHeight()) { count, gridSpan, itemSpan ->
            LazyVerticalGrid(
                GridCells.Fixed(count),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    count = items.itemCount,
                    key = items.itemKey {
                        it.id.toString()
                    },
                    span = {
                        if (items[it]?.poster != null) {
                            GridItemSpan(gridSpan)
                        } else {
                            GridItemSpan(itemSpan)
                        }
                    },
                ) { index ->
                    val communityInfo = items[index]
                    when {
                        communityInfo?.poster != null -> CommunityGrid(communityInfo, onClick)
                        else -> CommunityCell(communityInfo, false, onClick)
                    }
                }
            }
        }
    }
}

@Composable
fun CommunityConstrains(modifier: Modifier = Modifier, content: @Composable (Int, Int, Int) -> Unit) {
    BoxWithConstraints(modifier) {
        val gridCount = (maxWidth / 128.dp).toInt()
        val itemCount = (maxWidth / 160.dp).toInt()
        val lcm = lcm(gridCount, itemCount)
        content(lcm, lcm / gridCount, lcm / itemCount)
    }
}

@Composable
fun CommunityGrid(communityInfo: CommunityInfo?, onClick: ((CommunityInfo) -> Unit)? = null) {
    val appNav = LocalAppNav.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4)
            .clickable {
                communityInfo?.let { onClick?.invoke(it) ?: appNav.gotoCommunity(it.id, false) }
            }
    ) {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp))
                .fillMaxSize()
        )
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (communityInfo != null) {
                val commonDialogController = rememberCommonDialogController()
                val shown by commonDialogController.show
                CommunityIcon(communityInfo, shown, 30.dp, commonDialogController::update)
                Text(
                    communityInfo.name,
                    Modifier,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                    MaterialTheme.typography.labelSmall.fontSize
                )
            }
        }
    }
}

@Composable
fun CommunityCell(
    communityInfo: CommunityInfo?,
    customBackground: Boolean = false,
    onClick: ((CommunityInfo) -> Unit)? = null
) {
    val appNav = LocalAppNav.current
    Row(
        modifier = when {
            customBackground -> Modifier
            else -> {
                val shape = RoundedCornerShape(10.dp)
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer, shape)
                    .clip(shape)
                    .clickable {
                        communityInfo?.let { onClick?.invoke(it) ?: appNav.gotoCommunity(it.id, false) }
                    }
                    .padding(10.dp)
            }
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val commonDialogController = rememberCommonDialogController()
        val shown by commonDialogController.show
        CommunityIcon(communityInfo, shown, 50.dp, commonDialogController::update)
        Text(
            communityInfo?.name.orEmpty(),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
