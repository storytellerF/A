package com.storyteller_f.a.app.compose_app.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.compontents.CenterBox
import com.storyteller_f.a.app.compose_app.compontents.ExceptionCell
import com.storyteller_f.a.app.compose_app.compontents.ExceptionView
import com.storyteller_f.a.app.compose_app.no_content_yet
import com.storyteller_f.a.client.core.LoadingHandler
import com.storyteller_f.a.client.core.LoadingState
import com.storyteller_f.shared.model.PrimaryKeyIdentifiable
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

const val REFRESH_AFTER = 300L

private fun LoadState?.toLoadingState() =
    when (this) {
        null -> null

        is LoadState.Loading -> LoadingState.Loading

        is LoadState.Error -> LoadingState.Error(error)

        is LoadState.NotLoading -> LoadingState.Done
    }

@Composable
private fun CombinedLoadStates.toLoadingState(itemCount: Int): LoadingState? {
    val state by produceState<LoadingState?>(null, this, itemCount) {
        delay(100)
        value = source.refresh.toLoadingState()
    }
    return state
}

@Composable
fun <T> debounceState(v: T): T {
    val value by produceState(v, v) {
        delay(100)
        value = v
    }
    return value
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun <T : Any> StateView(
    pagingItems: LazyPagingItems<T>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var pullRefreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullRefreshState(refreshing = pullRefreshing, onRefresh = {
        pullRefreshing = true
        pagingItems.refresh()
    })
    val refreshLoadState = pagingItems.loadState.refresh
    LaunchedEffect(key1 = pullRefreshing, key2 = refreshLoadState) {
        // 增加延时，确保pagingItems真正进入刷新状态
        delay(REFRESH_AFTER)
        if (pullRefreshing) {
            // 刷新结束或者当前没有内容时停止刷新，如果没有内容会使用列表刷新控件
            if (refreshLoadState !is LoadState.Loading || pagingItems.itemCount == 0) {
                pullRefreshing = false
            }
        }
    }
    Box(modifier = modifier.pullRefresh(refreshState)) {
        if (pagingItems.itemCount > 0) {
            content()
        } else {
            when (val state = pagingItems.loadState.toLoadingState(pagingItems.itemCount)) {
                is LoadingState.Error -> CenterBox {
                    ExceptionCell(state.e) {
                        pagingItems.refresh()
                    }
                }

                is LoadingState.Done -> CenterBox {
                    Text(text = stringResource(Res.string.no_content_yet))
                }

                else -> CenterBox {
                    CircularProgressIndicator()
                }
            }
        }
        PullRefreshIndicator(pullRefreshing, refreshState, Modifier.align(Alignment.TopCenter))
    }
}

@OptIn(ExperimentalMaterialApi::class, FlowPreview::class)
@Composable
fun <T> StateView(
    handler: LoadingHandler<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T & Any) -> Unit,
) {
    val state by handler.state.collectAsState()
    val data by handler.data.collectAsState()
    var pullRefreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullRefreshState(refreshing = pullRefreshing, onRefresh = {
        pullRefreshing = true
        handler.refresh()
    })
    LaunchedEffect(key1 = pullRefreshing, key2 = state) {
        delay(REFRESH_AFTER)
        if (pullRefreshing && state !is LoadingState.Loading) pullRefreshing = false
    }
    Box(modifier = modifier.pullRefresh(refreshState)) {
        data.let { safeData ->
            if (safeData != null) {
                Column {
                    (state as? LoadingState.Error)?.let {
                        ExceptionCell(it.e) {
                            handler.refresh()
                        }
                    }
                    content(safeData)
                }
            } else {
                when (val capturedState = state) {
                    is LoadingState.Error -> {
                        CenterBox {
                            ExceptionCell(capturedState.e) {
                                handler.refresh()
                            }
                        }
                    }

                    else -> {
                        CenterBox {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        PullRefreshIndicator(pullRefreshing, refreshState, Modifier.align(Alignment.TopCenter))
    }
}

fun <T : PrimaryKeyIdentifiable> LazyListScope.nestedStateView(
    items: LazyPagingItems<T>,
    combinedLoadStates: CombinedLoadStates,
    content: @Composable (T?, Int) -> Unit,
) {
    topPrepend(combinedLoadStates) {
        items.refresh()
    }
    nestedStateList(items, content)
    bottomAppending(combinedLoadStates)
}

private fun <T : PrimaryKeyIdentifiable> LazyListScope.nestedStateList(
    items: LazyPagingItems<T>,
    content: @Composable (T?, Int) -> Unit,
) {
    items(
        items.itemCount,
        key = items.itemKey {
            it.id.toString()
        }
    ) {
        content(items[it], it)
    }
}

@Composable
fun <T : Any> RefCellStateView(
    handler: LoadingHandler<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val data by handler.data.collectAsState()
    val state by handler.state.collectAsState()
    val globalDialogController = LocalGlobalDialog.current
    val scope = rememberCoroutineScope()
    Box(modifier = modifier) {
        data.let {
            if (it != null) {
                content(it)
            } else {
                when (val localState = state) {
                    is LoadingState.Error -> Box(
                        modifier = Modifier.fillMaxSize().clickable {
                            scope.launch {
                                globalDialogController.showErrorMessage(localState.e)
                            }
                        }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ExceptionView(localState.e)
                    }

                    else -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

fun LazyListScope.topPrepend(combinedLoadStates: CombinedLoadStates, refresh: () -> Unit) {
    if (combinedLoadStates.prepend == LoadState.Loading) {
        item {
            RemoteMediatorLoadingView()
        }
    }
    val loadState = combinedLoadStates.mediator?.refresh
    if (loadState is LoadState.Error) {
        item {
            ExceptionCell(loadState.error, refresh)
        }
    } else if (combinedLoadStates.refresh is LoadState.Loading) {
        item {
            RemoteMediatorLoadingView()
        }
    }
}

@Composable
private fun RemoteMediatorLoadingView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(30.dp))
        Text(
            text = "Waiting for items to load from the backend",
            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
        )
    }
}

fun LazyGridScope.topPrepend(
    count: Int,
    combinedLoadStates: CombinedLoadStates,
    refresh: () -> Unit
) {
    if (combinedLoadStates.prepend == LoadState.Loading) {
        item(span = {
            GridItemSpan(count)
        }) {
            RemoteMediatorLoadingView()
        }
    }
    val loadState = combinedLoadStates.mediator?.refresh
    if (loadState is LoadState.Error) {
        item(span = {
            GridItemSpan(count)
        }) {
            ExceptionCell(loadState.error, refresh)
        }
    } else if (combinedLoadStates.refresh is LoadState.Loading) {
        item(span = { GridItemSpan(count) }) {
            RemoteMediatorLoadingView()
        }
    }
}

fun LazyListScope.bottomAppending(combinedLoadStates: CombinedLoadStates) {
    if (combinedLoadStates.append == LoadState.Loading) {
        item {
            RemoteMediatorLoadingView()
        }
    }
}

fun LazyGridScope.bottomAppending(
    count: Int,
    combinedLoadStates: CombinedLoadStates,
) {
    if (combinedLoadStates.append == LoadState.Loading) {
        item(span = {
            GridItemSpan(count)
        }) {
            RemoteMediatorLoadingView()
        }
    }
}
