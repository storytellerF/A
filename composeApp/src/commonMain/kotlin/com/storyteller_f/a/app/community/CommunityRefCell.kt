package com.storyteller_f.a.app.community

import androidx.compose.runtime.Composable
import com.storyteller_f.a.app.common.StateView2
import com.storyteller_f.shared.type.PrimaryKey
import moe.tlaster.precompose.viewmodel.viewModel

@Composable
fun CommunityRefCell(communityId: PrimaryKey, onClick: (PrimaryKey) -> Unit) {
    val viewModel = viewModel(CommunityViewModel::class, keys = listOf("community", communityId)) {
        CommunityViewModel(communityId)
    }
    StateView2(viewModel.handler) {
        CommunityCell(it, onClick)
    }
}

@Composable
fun CommunityRefCell(communityAid: String, onClick: (PrimaryKey) -> Unit) {
    val viewModel = viewModel(CommunityViewModel::class, keys = listOf("community", communityAid)) {
        CommunityViewModel(communityAid)
    }
    StateView2(viewModel.handler) {
        CommunityCell(it, onClick)
    }
}
