package com.storyteller_f.a.app.core.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.core.Res
import com.storyteller_f.a.app.core.common.PagingViewModel
import com.storyteller_f.a.app.core.no_content_yet
import com.storyteller_f.a.client.core.LoadingHandler
import com.storyteller_f.a.client.core.LoadingState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

const val REFRESH_AFTER = 300L

@OptIn(ExperimentalMaterialApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun <T : Any> StateView(
    pagingViewModel: PagingViewModel<T>,
    modifier: Modifier = Modifier,
    content: @Composable (LazyPagingItems<T>) -> Unit,
) {
    val pagingItems = pagingViewModel.flow.collectAsLazyPagingItems()
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
        if (pagingItems.itemSnapshotList.isNotEmpty()) {
            content(pagingItems)
            StateViewTopIndicator(pagingItems, pullRefreshing)
        } else {
            CenterBox {
                when (val state = (pagingItems.loadState.refresh).toLoadingState()) {
                    is LoadingState.Error -> ExceptionCell(state.e) {
                        pagingItems.refresh()
                    }

                    is LoadingState.Done ->
                        Text(text = stringResource(Res.string.no_content_yet))

                    else -> CircularProgressIndicator()
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
        val data by handler.data.collectAsState()
        HandlerStateViewInternal(data, handler, pullRefreshing, content)
        PullRefreshIndicator(pullRefreshing, refreshState, Modifier.align(Alignment.TopCenter))
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun <T> HandlerStateViewInternal(
    data: T?,
    handler: LoadingHandler<T>,
    pullRefreshing: Boolean,
    content: @Composable ((T & Any) -> Unit)
) {
    val state by handler.state.collectAsState()
    if (data != null) {
        content(data)
        StateViewTopIndicator(state, handler, pullRefreshing)
    } else {
        CenterBox {
            when (val capturedState = state) {
                is LoadingState.Error -> {
                    ExceptionCell(capturedState.e) {
                        handler.refresh()
                    }
                }

                else -> {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun <T> StateViewTopIndicator(
    state: LoadingState?,
    handler: LoadingHandler<T>,
    pullRefreshing: Boolean
) {
    val newState = if (state is LoadingState.Loading || pullRefreshing) LoadingState.Done else state
    StateViewTopIndicatorInternal(newState) {
        handler.refresh()
    }
}

@Composable
private fun <T : Any> StateViewTopIndicator(
    pagingItems: LazyPagingItems<T>,
    pullRefreshing: Boolean
) {
    val combinedLoadStates = pagingItems.loadState
    val refreshState = combinedLoadStates.refresh.toLoadingState()
    val newState = if (pullRefreshing && refreshState is LoadingState.Loading) {
        LoadingState.Done
    } else {
        refreshState
    }
    StateViewTopIndicatorInternal(
        newState
    ) {
        pagingItems.refresh()
    }
}

class LoadingStatePreviewProvider : PreviewParameterProvider<LoadingState> {
    override val values: Sequence<LoadingState>
        get() = sequenceOf(
            LoadingState.Loading,
            LoadingState.Done,
            LoadingState.Error(Exception())
        )
}

@Preview
@Composable
private fun StateViewTopIndicatorInternal(
    @PreviewParameter(LoadingStatePreviewProvider::class) loadingState: LoadingState?,
    refresh: () -> Unit = {},
) {
    val visible = loadingState is LoadingState.Error || loadingState is LoadingState.Loading
    val shape = RoundedCornerShape(8.dp)
    AnimatedVisibility(visible) {
        Box(
            Modifier.padding(16.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            if (loadingState is LoadingState.Error) {
                ExceptionCell(loadingState.e) {
                    refresh()
                }
            } else if (loadingState is LoadingState.Loading) {
                RemoteMediatorLoadingView()
            }
        }
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
    Box(modifier = modifier) {
        data.let {
            if (it != null) {
                content(it)
            } else {
                when (val localState = state) {
                    is LoadingState.Error -> Box(
                        modifier = Modifier.fillMaxSize().clickable {
                            // TODO show message
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

fun LazyListScope.topPrepend(combinedLoadStates: CombinedLoadStates) {
    if (combinedLoadStates.prepend == LoadState.Loading) {
        item("prepend") {
            RemoteMediatorLoadingView()
        }
    }
}

@Composable
private fun RemoteMediatorLoadingView() {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = "Waiting for items to load from the backend",
            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
        )
    }
}

fun LazyGridScope.topPrepend(
    combinedLoadStates: CombinedLoadStates
) {
    if (combinedLoadStates.prepend == LoadState.Loading) {
        item("prepend", span = {
            GridItemSpan(maxLineSpan)
        }) {
            RemoteMediatorLoadingView()
        }
    }
}

fun LazyListScope.bottomAppending(combinedLoadStates: CombinedLoadStates) {
    if (combinedLoadStates.append == LoadState.Loading) {
        item("append") {
            RemoteMediatorLoadingView()
        }
    }
}

fun LazyGridScope.bottomAppending(
    combinedLoadStates: CombinedLoadStates,
) {
    if (combinedLoadStates.append == LoadState.Loading) {
        item("append", span = {
            GridItemSpan(maxLineSpan)
        }) {
            RemoteMediatorLoadingView()
        }
    }
}

fun <T : Any> LazyListScope.pagingItems(
    lazyPagingItems: LazyPagingItems<T>,
    key: ((it: T) -> Any)? = null,
    contentType: (index: Int) -> Any? = { null },
    itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
) {
    val k = if (key != null) {
        lazyPagingItems.itemKey {
            key(it)
        }
    } else {
        null
    }
    items(lazyPagingItems.itemSnapshotList.size, k, contentType, itemContent)
}

fun <T : Any> LazyGridScope.pagingItems(
    lazyPagingItems: LazyPagingItems<T>,
    key: ((index: T) -> Any)? = null,
    span: (LazyGridItemSpanScope.(index: T) -> GridItemSpan)? = null,
    contentType: (index: T) -> Any? = { null },
    itemContent: @Composable LazyGridItemScope.(index: Int) -> Unit,
) {
    val k = if (key != null) {
        lazyPagingItems.itemKey {
            key(it)
        }
    } else {
        null
    }
    val c = lazyPagingItems.itemContentType {
        contentType(it)
    }
    val s: (LazyGridItemSpanScope.(Int) -> GridItemSpan)? = if (span != null) {
        {
            span(lazyPagingItems[it]!!)
        }
    } else {
        null
    }
    items(lazyPagingItems.itemCount, k, s, c, itemContent)
}

private fun LoadState?.toLoadingState() = when (this) { null -> null

    is LoadState.Loading -> LoadingState.Loading

    is LoadState.Error -> LoadingState.Error(error)

    is LoadState.NotLoading -> LoadingState.Done
}

@Composable
fun debounce(v: LoadState): LoadingState? {
    val debounced by produceState<LoadingState?>(null, v) {
        if (v is LoadState.NotLoading) {
            delay(500)
        }
        value = v.toLoadingState()
    }
    return debounced
}
