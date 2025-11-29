package com.storyteller_f.a.app.pages.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.LocalAppNavFactory
import com.storyteller_f.a.app.common.CommunitiesViewModel
import com.storyteller_f.a.app.common.CommunityScreen
import com.storyteller_f.a.app.common.createJoinedCommunitiesViewModel
import com.storyteller_f.a.app.common.createJoinedCommunitiesWithPosterViewModel
import com.storyteller_f.a.app.common.hasRouteFlow
import com.storyteller_f.a.app.core.components.CommunityPoster
import com.storyteller_f.a.app.core.components.LayoutDefaults
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.bottomAppending
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.app.core.components.rememberCommonDialogController
import com.storyteller_f.a.app.core.components.topPrepend
import com.storyteller_f.shared.model.CommunityInfo
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

@Composable
fun MyCommunitiesPage() {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Column {
        PrimaryTabRow(pagerState.currentPage) {
            listOf("All", "Poster").forEachIndexed { i, label ->
                Tab(selected = pagerState.currentPage == i, onClick = {
                    scope.launch { pagerState.scrollToPage(i) }
                }) {
                    Text(label, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
        HorizontalPager(pagerState, modifier = Modifier.weight(1f)) { index ->
            when (index) {
                0 -> CommunityCellList()
                else -> CommunityPosterGrid()
            }
        }
    }
}

@Composable
fun CommunityCellList(
    onClick: ((CommunityInfo) -> Unit)? = null
) {
    val cellViewModel = createJoinedCommunitiesViewModel()
    CommunityList(cellViewModel, onClick)
}

@Composable
fun CommunityPosterGrid(
    onClick: ((CommunityInfo) -> Unit)? = null
) {
    val posterViewModel = createJoinedCommunitiesWithPosterViewModel()
    StateView(posterViewModel, modifier = Modifier.fillMaxSize()) { items ->
        LazyVerticalGrid(
            GridCells.Adaptive(minSize = 128.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = LayoutDefaults.contentPadding,
            horizontalArrangement = LayoutDefaults.pagingHorizontalArrangement,
            verticalArrangement = LayoutDefaults.pagingVerticalArrangement
        ) {
            topPrepend(items.loadState)
            pagingItems(
                items,
                { it.id.toString() }
            ) { index ->
                val info = items[index]
                if (info != null) {
                    CommunityGrid(info, 0.dp, onClick)
                }
            }
            bottomAppending(items.loadState)
        }
    }
}

@Composable
fun CommunityList(
    communitiesViewModel: CommunitiesViewModel,
    onClick: ((CommunityInfo) -> Unit)? = null
) {
    StateView(communitiesViewModel, modifier = Modifier.fillMaxSize()) { items ->
        LazyColumn(
            contentPadding = LayoutDefaults.contentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            topPrepend(items.loadState)
            items(
                count = items.itemSnapshotList.size,
                key = items.itemKey {
                    it.id.toString()
                },
            ) { index ->
                val info = items[index]
                CommunityCell(info, false, onClick)
            }
            bottomAppending(items.loadState)
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun CommunityGrid(
    communityInfo: CommunityInfo?,
    padding: Dp,
    onClick: ((CommunityInfo) -> Unit)? = null
) {
    val appNavFactory = LocalAppNavFactory.current
    val hazeState = rememberHazeState()
    Box(modifier = Modifier.fillMaxWidth().padding(end = padding)) {
        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4)
                .clip(shape)
                .clickable {
                    communityInfo?.let {
                        onClick?.invoke(it) ?: appNavFactory.newAppNav().gotoCommunity(it.id, false)
                    }
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
                    CommunityIconWithDialog(communityInfo, shown, 30.dp, onClickIcon = commonDialogController::update)
                    Text(
                        communityInfo.name,
                        Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
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
    val appNavFactory = LocalAppNavFactory.current
    val isCommunityPage by appNavFactory.hasRouteFlow<CommunityScreen> {
        it.communityId == communityInfo?.id
    }
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
                        communityInfo?.let {
                            onClick?.invoke(it) ?: appNavFactory.newAppNav().gotoCommunity(
                                it.id,
                                false
                            )
                        }
                    }
                    .padding(10.dp)
            }
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val commonDialogController = rememberCommonDialogController()
        val shown by commonDialogController.shown
        CommunityIconWithDialog(communityInfo, shown, 50.dp, onClickIcon = commonDialogController::update)
        Text(
            communityInfo?.name.orEmpty(),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
