package com.storyteller_f.a.app.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.storyteller_f.a.app.CustomBottomNav
import com.storyteller_f.a.app.community.CommunityDialogInternal
import com.storyteller_f.a.app.community.communityNavRoutes
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.AddTaskValue
import com.storyteller_f.shared.utils.now
import kotlinx.serialization.json.Json
import java.io.File


@Preview
@Composable
private fun PreviewCommunity(@PreviewParameter(CommunityProvider::class) communityInfo: CommunityInfo) {
    CommunityDialogInternal(communityInfo = communityInfo)
}

@Preview
@Composable
private fun PreviewCommunityPage() {
    Column {
        CustomSearchBar {

        }
        CustomBottomNav(null, navRoutes = communityNavRoutes())
    }
}

private class CommunityProvider : PreviewParameterProvider<CommunityInfo> {
    override val values: Sequence<CommunityInfo>
        get() = sequence {
            val f = File(com.storyteller_f.a.app.BuildKonfig.PROJECT_PATH, "../../AData/data/preset_community.json")
            if (f.exists()) {
                val value = Json.decodeFromString<AddTaskValue>(f.readText())
                yieldAll(value.communityData.orEmpty().map {
                    CommunityInfo(0u, "", it.name, 0u, now(), null, null, now())
                })
            }
        }

}
