/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.app.pages

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.client.compose_core.components.CustomBottomNav
import com.storyteller_f.a.client.compose_core.components.CustomRailNav
import com.storyteller_f.a.client.compose_core.components.NavRoute
import kotlinx.coroutines.launch

/** Both navigation presentations share one pager and keep section content mounted across resizes. */
@Composable
fun HomeSectionScaffold(
    pagerState: PagerState,
    routes: List<NavRoute>,
    hasUnread: Boolean,
    modifier: Modifier = Modifier,
    search: @Composable (Int, Boolean) -> Unit,
    section: @Composable (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val searchStates = rememberSaveableStateHolder()
    val selectSection: (String) -> Unit = { path ->
        scope.launch { pagerState.animateScrollToPage(routes.indexOfFirst { it.path == path }) }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        Scaffold(bottomBar = {
            if (compact) CustomBottomNav(routes[pagerState.currentPage].path, routes, hasUnread, selectSection)
        }) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (!compact) CustomRailNav(routes[pagerState.currentPage].path, routes, hasUnread, selectSection)
                Column(Modifier.weight(1f).fillMaxSize()) {
                    searchStates.SaveableStateProvider(routes[pagerState.currentPage].path) {
                        search(pagerState.currentPage, compact)
                    }
                    HorizontalPager(
                        pagerState,
                        Modifier.weight(1f),
                        userScrollEnabled = compact,
                    ) { section(it) }
                }
            }
        }
    }
}
