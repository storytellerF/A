package com.storyteller_f.a.app.common

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
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.compontents.CenterBox
import com.storyteller_f.a.app.compontents.ExceptionCell
import com.storyteller_f.a.app.compontents.ExceptionView
import com.storyteller_f.a.app.no_content_yet
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
        val mediatorRefreshState = mediator?.refresh
        val sourceRefreshState = source.refresh
        value = when {
            mediatorRefreshState is LoadState.Error && itemCount == 0 -> refresh.toLoadingState()
            else -> sourceRefreshState.toLoadingState()
        }
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
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullRefreshState(refreshing = refreshing, onRefresh = {
        refreshing = true
        pagingItems.refresh()
    })
    val refreshLoadState = pagingItems.loadState.refresh
    LaunchedEffect(key1 = refreshing, key2 = refreshLoadState) {
        // 增加延时，确保pagingItems真正进入刷新状态
        delay(REFRESH_AFTER)
        if (refreshing) {
            // 刷新结束或者当前没有内容时停止刷新，如果没有内容会使用列表刷新控件
            if (refreshLoadState !is LoadState.Loading || pagingItems.itemCount == 0) refreshing = false
        }
    }
    Box(modifier = modifier.pullRefresh(refreshState)) {
        when (val state = pagingItems.loadState.toLoadingState(pagingItems.itemCount)) {
            null -> CenterBox {
                CircularProgressIndicator()
            }

            is LoadingState.Loading -> if (pagingItems.itemCount == 0) {
                CenterBox {
                    CircularProgressIndicator()
                }
            } else {
                content()
            }

            is LoadingState.Error -> CenterBox {
                ExceptionCell(state.e) {
                    pagingItems.refresh()
                }
            }

            is LoadingState.Done -> if (pagingItems.itemCount == 0) {
                CenterBox {
                    Text(text = stringResource(Res.string.no_content_yet))
                }
            } else {
                content()
            }
        }
        PullRefreshIndicator(refreshing, refreshState, Modifier.align(Alignment.TopCenter))
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
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullRefreshState(refreshing = refreshing, onRefresh = {
        refreshing = true
        handler.refresh()
    })
    LaunchedEffect(key1 = refreshing, key2 = state) {
        delay(REFRESH_AFTER)
        if (refreshing && state !is LoadingState.Loading) refreshing = false
    }
    Box(modifier = modifier.pullRefresh(refreshState)) {
        when (val s = debounceState(state)) {

            is LoadingState.Error -> {
                data.let {
                    if (it == null) {
                        CenterBox {
                            ExceptionCell(s.e) {
                                handler.refresh()
                            }
                        }
                    } else {
                        content(it)
                    }
                }
            }

            is LoadingState.Done -> data?.let {
                content(it)
            }

            else -> {
                data.let {
                    if (it == null) {
                        CenterBox {
                            CircularProgressIndicator()
                        }
                    } else {
                        content(it)
                    }
                }
            }
        }
        PullRefreshIndicator(refreshing, refreshState, Modifier.align(Alignment.TopCenter))
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
        when (val localState = state) {
            null, is LoadingState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

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
                data?.let {
                    content(it)
                }
            }
        }
    }
}


fun LazyListScope.topPrepend(combinedLoadStates: CombinedLoadStates, refresh: () -> Unit) {
    if (combinedLoadStates.prepend == LoadState.Loading) {
        item {
            Text(
                text = "Waiting for items to load from the backend",
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
            )
        }
    }
    val loadState = combinedLoadStates.mediator?.refresh
    if (loadState is LoadState.Error) {
        item {
            ExceptionCell(loadState.error, refresh)
        }
    }
}

fun LazyGridScope.topPrepend(count: Int, combinedLoadStates: CombinedLoadStates, refresh: () -> Unit) {
    if (combinedLoadStates.prepend == LoadState.Loading) {
        item(span = {
            GridItemSpan(count)
        }) {
            Text(
                text = "Waiting for items to load from the backend",
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
            )
        }
    }
    val loadState = combinedLoadStates.mediator?.refresh
    if (loadState is LoadState.Error) {
        item(span = {
            GridItemSpan(count)
        }) {
            ExceptionCell(loadState.error, refresh)
        }
    }
}

fun LazyListScope.bottomAppending(combinedLoadStates: CombinedLoadStates) {
    if (combinedLoadStates.append == LoadState.Loading) {
        item {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
            )
        }
    }
}

fun LazyGridScope.bottomAppending(
    count: Int, combinedLoadStates: CombinedLoadStates,
) {
    if (combinedLoadStates.append == LoadState.Loading) {
        item(span = {
            GridItemSpan(count)
        }) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
            )
        }
    }
}