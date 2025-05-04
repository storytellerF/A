package com.storyteller_f.a.app.pages.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.common.StateView
import com.storyteller_f.a.app.common.bottomAppending
import com.storyteller_f.a.app.common.topPrepend
import com.storyteller_f.a.app.compontents.CommunityIcon
import com.storyteller_f.a.app.compontents.CommunityPoster
import com.storyteller_f.a.app.compontents.rememberCommonDialogController
import com.storyteller_f.a.app.model.createJoinedCommunitiesViewModel
import com.storyteller_f.a.app.utils.lcm
import com.storyteller_f.shared.model.CommunityInfo
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun MyCommunitiesPage() {
    val viewModel = createJoinedCommunitiesViewModel()
    val items = viewModel.flow.collectAsLazyPagingItems()
    CommunityList(items)
}

@Composable
fun CommunityList(items: LazyPagingItems<CommunityInfo>, onClick: ((CommunityInfo) -> Unit)? = null) {
    StateView(items, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints {
            val gridCount = (this.maxWidth / 128.dp).toInt()
            val itemCount = (this.maxWidth / 160.dp).toInt()
            val lcm = lcm(gridCount, itemCount)
            val gridSpan = lcm / gridCount
            LazyVerticalGrid(
                GridCells.Fixed(lcm),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                topPrepend(items, lcm)
                items(
                    count = items.itemCount,
                    key = items.itemKey {
                        it.id.toString()
                    },
                    span = {
                        when {
                            items[it]?.poster == null -> GridItemSpan(lcm / itemCount)
                            items.itemCount > it + 1 && items[it + 1]?.poster == null -> {
                                val t = (it + 1) % gridCount
                                if (t == 0) {
                                    GridItemSpan(gridSpan)
                                } else {
                                    GridItemSpan((gridCount + 1 - t) * gridSpan)
                                }
                            }

                            else -> {
                                GridItemSpan(gridSpan)
                            }
                        }
                    },
                ) { index ->
                    val communityInfo = items[index]
                    when {
                        communityInfo?.poster != null -> {
                            val width = if (index + 1 < items.itemCount && items[index + 1]?.poster == null) {
                                val t = (index + 1) % gridCount
                                if (t == 0) {
                                    0.dp
                                } else {
                                    val itemWidth =
                                        (this@BoxWithConstraints.maxWidth - (gridCount - 1) * 10.dp - 40.dp) / gridCount
                                    (gridCount - t) * itemWidth + (gridCount - t) * 10.dp
                                }
                            } else
                                0.dp
                            CommunityGrid(communityInfo, width, onClick)
                        }

                        else -> CommunityCell(communityInfo, false, onClick)
                    }
                }
                bottomAppending(items, lcm)
            }
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun CommunityGrid(communityInfo: CommunityInfo?, padding: Dp, onClick: ((CommunityInfo) -> Unit)? = null) {
    val appNav = LocalAppNav.current
    val hazeState = rememberHazeState()
    Box(modifier = Modifier.fillMaxWidth().padding(end = padding)) {
        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4)
                .clip(shape)
                .clickable {
                    communityInfo?.let { onClick?.invoke(it) ?: appNav.gotoCommunity(it.id, false) }
                }
        ) {
            Box(modifier = Modifier.hazeSource(hazeState)) {
                CommunityPoster(communityInfo)
            }
            val radius = 8.dp
            val shape = RoundedCornerShape(radius)
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                    .clip(shape)
                    .hazeEffect(hazeState, HazeMaterials.ultraThin()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (communityInfo != null) {
                    val commonDialogController = rememberCommonDialogController()
                    val shown by commonDialogController.show
                    CommunityIcon(communityInfo, shown, 30.dp, onClickIcon = commonDialogController::update)
                    Text(
                        communityInfo.name,
                        Modifier.fillMaxWidth(),
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        MaterialTheme.typography.labelSmall.fontSize
                    )
                }
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
        CommunityIcon(communityInfo, shown, 50.dp, onClickIcon = commonDialogController::update)
        Text(
            communityInfo?.name.orEmpty(),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
