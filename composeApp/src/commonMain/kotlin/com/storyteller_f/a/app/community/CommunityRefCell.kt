package com.storyteller_f.a.app.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.common.RefCellStateView
import com.storyteller_f.a.app.model.CommunityViewModel
import com.storyteller_f.a.app.model.createCommunityViewModel
import com.storyteller_f.shared.type.PrimaryKey

@Composable
fun CommunityRefCell(communityId: PrimaryKey) {
    val viewModel = createCommunityViewModel(communityId)
    CommunityRefCellInternal(viewModel)
}

@Composable
fun CommunityRefCell(communityAid: String) {
    val viewModel = createCommunityViewModel(communityAid)
    CommunityRefCellInternal(viewModel)
}

@Composable
private fun CommunityRefCellInternal(viewModel: CommunityViewModel) {
    val communityInfo by viewModel.handler.data.collectAsState()
    val shape = RoundedCornerShape(10.dp)
    val appNav = LocalAppNav.current
    RefCellStateView(
        viewModel.handler,
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, shape)
            .clip(shape)
            .clickable {
                communityInfo?.let { appNav.gotoCommunity(it.id, false) }
            }
            .padding(10.dp)
    ) {
        CommunityCell(it, true)
    }
}
