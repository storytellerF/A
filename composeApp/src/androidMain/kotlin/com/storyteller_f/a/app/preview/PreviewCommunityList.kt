package com.storyteller_f.a.app.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.community.CommunityCell
import com.storyteller_f.a.app.community.CommunityConstrains
import com.storyteller_f.a.app.community.CommunityGrid
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.AddTaskValue
import com.storyteller_f.shared.utils.now
import kotlinx.serialization.json.Json
import java.io.File

private class CommunitiesProvider : PreviewParameterProvider<List<CommunityInfo>> {
    override val values: Sequence<List<CommunityInfo>>
        get() = sequence {
            val f = File(com.storyteller_f.a.app.BuildKonfig.PROJECT_PATH, "../../AData/data/preset_community.json")
            if (f.exists()) {
                val value = Json.decodeFromString<AddTaskValue>(f.readText())
                yield(value.communityData.orEmpty().map {
                    CommunityInfo(0u, "", it.name, 0u, now(), null, null, now())
                })
            }
        }
}

@Preview
@Composable
private fun PreviewCommunities(@PreviewParameter(CommunitiesProvider::class) communities: List<CommunityInfo>) {
    CommunityConstrains { count, gridSpan, itemSpan ->
        LazyVerticalGrid(
            GridCells.Fixed(count),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(communities, span = {
                if (it.poster != null) {
                    GridItemSpan(gridSpan)
                } else {
                    GridItemSpan(itemSpan)
                }
            }) {
                if (it.poster != null) {
                    CommunityGrid(it)
                } else {
                    CommunityCell(it)
                }
            }
        }
    }
}
