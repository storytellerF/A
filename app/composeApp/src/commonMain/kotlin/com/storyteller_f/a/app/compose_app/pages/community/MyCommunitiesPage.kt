package com.storyteller_f.a.app.compose_app.pages.community

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
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.compose_app.CommunityScreen
import com.storyteller_f.a.app.compose_app.LocalAppNav
import com.storyteller_f.a.app.compose_app.compontents.CommunityIcon
import com.storyteller_f.a.app.compose_app.compontents.CommunityPoster
import com.storyteller_f.a.app.core.compontents.StateView
import com.storyteller_f.a.app.core.compontents.bottomAppending
import com.storyteller_f.a.app.compose_app.compontents.rememberCommonDialogController
import com.storyteller_f.a.app.core.compontents.topPrepend
import com.storyteller_f.a.app.compose_app.model.CommunitiesViewModel
import com.storyteller_f.a.app.compose_app.model.createJoinedCommunitiesViewModel
import com.storyteller_f.a.app.compose_app.toRoute
import com.storyteller_f.a.app.core.utils.lcm
import com.storyteller_f.shared.model.CommunityInfo
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun MyCommunitiesPage() {
    val viewModel = createJoinedCommunitiesViewModel()
    CommunityList(viewModel)
}

@Composable
fun CommunityList(communitiesViewModel: CommunitiesViewModel, onClick: ((CommunityInfo) -> Unit)? = null) {
    StateView(communitiesViewModel, modifier = Modifier.fillMaxSize()) { items ->
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
                topPrepend(lcm, items.loadState)
                items(
                    count = items.itemSnapshotList.size,
                    key = items.itemKey {
                        it.id.toString()
                    },
                    span = {
                        getCommunityGridSpan(
                            it,
                            lcm,
                            itemCount,
                            gridCount,
                            gridSpan,
                            items.itemCount
                        ) {
                            items[it]
                        }
                    },
                ) { index ->
                    val communityInfo = items[index]
                    when {
                        communityInfo?.hasPoster == true -> {
                            val padding = getCommunityGridEndPadding(index, items, gridCount)
                            CommunityGrid(communityInfo, padding, onClick)
                        }

                        else -> CommunityCell(communityInfo, false, onClick)
                    }
                }
                bottomAppending(lcm, items.loadState)
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.getCommunityGridEndPadding(
    index: Int,
    items: LazyPagingItems<CommunityInfo>,
    gridCount: Int
): Dp {
    val padding = if (index + 1 < items.itemSnapshotList.size && items[index + 1]?.poster == null) {
        val t = (index + 1) % gridCount
        if (t == 0) {
            0.dp
        } else {
            val itemWidth =
                (this.maxWidth - (gridCount - 1) * 10.dp - 40.dp) / gridCount
            (gridCount - t) * itemWidth + (gridCount - t) * 10.dp
        }
    } else {
        0.dp
    }
    return padding
}

private fun getCommunityGridSpan(
    i: Int,
    lcm: Int,
    itemCount: Int,
    gridCount: Int,
    gridSpan: Int,
    pagingItemCount: Int,
    getItem: (Int) -> CommunityInfo?
): GridItemSpan = when {
    getItem(i)?.poster == null -> GridItemSpan(lcm / itemCount)
    pagingItemCount > i + 1 && getItem(i + 1)?.poster == null -> {
        val t = (i + 1) % gridCount
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
            val shape = RoundedCornerShape(10.dp)
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                    .clip(shape)
                    .hazeEffect(hazeState, HazeMaterials.ultraThin()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (communityInfo != null) {
                    val commonDialogController =
                        rememberCommonDialogController()
                    val shown by commonDialogController.shown
                    CommunityIcon(
                        communityInfo,
                        shown,
                        30.dp,
                        onClickIcon = commonDialogController::update
                    )
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
    val isCommunityPage = appNav.toRoute<CommunityScreen>()?.communityId == communityInfo?.id
    Row(
        modifier = when {
            customBackground -> Modifier
            else -> {
                val shape = RoundedCornerShape(10.dp)
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer, shape)
                    .clip(shape)
                    .clickable(!isCommunityPage) {
                        communityInfo?.let { onClick?.invoke(it) ?: appNav.gotoCommunity(it.id, false) }
                    }
                    .padding(10.dp)
            }
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val commonDialogController =
            rememberCommonDialogController()
        val shown by commonDialogController.shown
        CommunityIcon(
            communityInfo,
            shown,
            50.dp,
            onClickIcon = commonDialogController::update
        )
        Text(
            communityInfo?.name.orEmpty(),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
