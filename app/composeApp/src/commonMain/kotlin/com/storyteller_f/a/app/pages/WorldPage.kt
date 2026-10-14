/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.app.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.IAccountInstance
import com.storyteller_f.a.app.LocalAppNavFactory
import com.storyteller_f.a.app.LocalUiViewModel
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.common.createJoinedCommunitiesViewModel
import com.storyteller_f.a.app.common.createWorldViewModel
import com.storyteller_f.a.app.communities
import com.storyteller_f.a.app.home_context_description
import com.storyteller_f.a.app.pages.community.CommunityCell
import com.storyteller_f.a.app.pages.topic.TopicList
import com.storyteller_f.a.app.view_all
import com.storyteller_f.a.client.compose_core.components.SignInButton
import com.storyteller_f.a.client.compose_core.components.StateView
import org.jetbrains.compose.resources.stringResource

@Composable
fun WorldPage(selectSection: (Int) -> Unit = {}) {
    val viewModel = createWorldViewModel()
    HomeFeedLayout(context = { HomeCommunityContext { selectSection(1) } }) {
        TopicList(viewModel)
    }
}

@Composable
private fun HomeCommunityContext(openCommunities: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(Res.string.communities),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = openCommunities) { Text(stringResource(Res.string.view_all)) }
            }
            Text(stringResource(Res.string.home_context_description), style = MaterialTheme.typography.bodySmall)
            val instance by LocalUiViewModel.current.instance.collectAsState()
            if (instance is IAccountInstance.None) {
                val appNavFactory = LocalAppNavFactory.current
                SignInButton { appNavFactory.newAppNav().gotoSignIn() }
            } else {
                HomeCommunityPreview(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HomeCommunityPreview(modifier: Modifier) {
    val viewModel = createJoinedCommunitiesViewModel()
    StateView(viewModel, modifier.fillMaxSize(), showRefresh = true) { items ->
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(count = minOf(items.itemCount, 5), key = items.itemKey { it.id }) { index ->
                CommunityCell(items[index])
            }
        }
    }
}
