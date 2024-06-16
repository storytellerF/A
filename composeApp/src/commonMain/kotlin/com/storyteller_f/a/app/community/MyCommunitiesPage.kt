package com.storyteller_f.a.app.community

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
import androidx.compose.ui.unit.dp
import androidx.paging.ExperimentalPagingApi
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemContentType
import app.cash.paging.compose.itemKey
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.compontents.CommunityIcon
import com.storyteller_f.a.app.utils.lcm
import com.storyteller_f.a.client_lib.getJoinCommunities
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.type.OKey
import moe.tlaster.precompose.viewmodel.viewModel


@Composable
fun MyCommunitiesPage(onClick: (OKey) -> Unit) {
    val viewModel = viewModel(MyCommunitiesViewModel::class) {
        MyCommunitiesViewModel()
    }
    val items = viewModel.flow.collectAsLazyPagingItems()
    StateView(items) {
        CommunityConstrains(modifier = Modifier.fillMaxHeight()) { count, gridSpan, itemSpan ->
            LazyVerticalGrid(
                GridCells.Fixed(count),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    count = items.itemCount,
                    key = items.itemKey {
                        it.id
                    },
                    span = {
                        if (items[it]?.poster != null) {
                            GridItemSpan(gridSpan)
                        } else {
                            GridItemSpan(itemSpan)
                        }
                    },
                    contentType = items.itemContentType()
                ) { index ->
                    val communityInfo = items[index]
                    when {
                        communityInfo?.poster != null -> CommunityGrid(communityInfo, onClick)
                        else -> CommunityCell(communityInfo, onClick)
                    }
                }
            }
        }
    }

}

@OptIn(ExperimentalPagingApi::class)
class MyCommunitiesViewModel : PagingViewModel<Int, CommunityInfo>({
    SimplePagingSource {
        serviceCatching {
            client.getJoinCommunities()
        }.map {
            APagingData(it.data, null)
        }

    }
})


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
fun CommunityGrid(communityInfo: CommunityInfo?, onClick: (OKey) -> Unit = {}) {
    Box(modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(3f / 4)
        .clickable {
            communityInfo?.let { onClick(it.id) }
        }) {
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
                CommunityIcon(communityInfo, 30.dp)
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
fun CommunityCell(communityInfo: CommunityInfo?, onClick: (OKey) -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp))
            .clickable {
                communityInfo?.id?.let { onClick(it) }
            }
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CommunityIcon(communityInfo, 50.dp)
        Text(
            communityInfo?.name.orEmpty(),
            Modifier,
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.typography.labelSmall.fontSize
        )
    }

}
