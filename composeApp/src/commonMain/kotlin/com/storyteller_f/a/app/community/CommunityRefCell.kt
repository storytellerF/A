package com.storyteller_f.a.app.community

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.storyteller_f.a.app.common.StateView2
import com.storyteller_f.shared.type.OKey
import moe.tlaster.precompose.viewmodel.viewModel

@Composable
fun CommunityRefCell(communityId: OKey, onClick: (OKey) -> Unit) {
    val viewModel = viewModel(CommunityViewModel::class, keys = listOf("community", communityId)) {
        CommunityViewModel(communityId)
    }
    StateView2(viewModel.handler) {
        CommunityCell(it, onClick)
    }
}
