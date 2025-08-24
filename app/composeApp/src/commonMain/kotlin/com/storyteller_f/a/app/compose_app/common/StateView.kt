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
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.compontents.CenterBox
import com.storyteller_f.a.app.compose_app.compontents.CustomAlertDialog
import com.storyteller_f.a.app.compose_app.compontents.ExceptionCell
import com.storyteller_f.a.app.compose_app.compontents.ExceptionView
import com.storyteller_f.a.app.compose_app.compontents.rememberAlertDialogController
import com.storyteller_f.a.app.compose_app.no_content_yet
import com.storyteller_f.a.client.core.LoadingHandler
import com.storyteller_f.a.client.core.LoadingState
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
        if (pagingItems.itemSnapshotList.isNotEmpty()) {
            Column {
                LoadStateAtTop(pagingItems, pullRefreshing)
                Box(Modifier.weight(1f)) {
                    content()
                }
            }
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
                    when (val capturedState = state) {
                        is LoadingState.Error -> ExceptionCell(capturedState.e) {
                            handler.refresh()
                        }

                        is LoadingState.Done -> {
                        }

                        else -> if (!pullRefreshing) RemoteMediatorLoadingView()
                    }
                    Box(Modifier.weight(1f)) {
                        content(safeData)
                    }
                }
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

        PullRefreshIndicator(pullRefreshing, refreshState, Modifier.align(Alignment.TopCenter))
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
    val alertDialogController = rememberAlertDialogController()
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
                                alertDialogController.showErrorMessage(localState.e)
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
    CustomAlertDialog(alertDialogController, {
        alertDialogController.close()
    }) {
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
private fun <T : Any> LoadStateAtTop(pagingItems: LazyPagingItems<T>, pullRefreshing: Boolean) {
    val combinedLoadStates = pagingItems.loadState
    val refreshState = combinedLoadStates.refresh
    if (refreshState is LoadState.Error) {
        ExceptionCell(refreshState.error) {
            pagingItems.refresh()
        }
    } else if (refreshState is LoadState.Loading && !pullRefreshing) {
        RemoteMediatorLoadingView()
    }
}

@Composable
private fun RemoteMediatorLoadingView() {
    Column(
        modifier = Modifier.padding(8.dp),
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
    count: Int,
    combinedLoadStates: CombinedLoadStates
) {
    if (combinedLoadStates.prepend == LoadState.Loading) {
        item("prepend", span = {
            GridItemSpan(count)
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
    count: Int,
    combinedLoadStates: CombinedLoadStates,
) {
    if (combinedLoadStates.append == LoadState.Loading) {
        item("append", span = {
            GridItemSpan(count)
        }) {
            RemoteMediatorLoadingView()
        }
    }
}
