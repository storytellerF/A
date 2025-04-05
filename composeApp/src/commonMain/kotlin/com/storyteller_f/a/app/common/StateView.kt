package com.storyteller_f.a.app.common

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.no_content_yet
import a.composeapp.generated.resources.refresh
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cash.paging.LoadState
import app.cash.paging.LoadStateError
import app.cash.paging.LoadStateLoading
import app.cash.paging.LoadStateNotLoading
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.itemKey
import com.storyteller_f.a.app.compontents.ExceptionView
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.client_lib.LoadingHandler
import com.storyteller_f.a.client_lib.LoadingState
import com.storyteller_f.shared.model.Identifiable
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

const val REFRESH_AFTER = 300L

private fun LoadState?.toLoadingState() =
    when (this) {
        null -> null

        is LoadStateLoading -> LoadingState.Loading

        is LoadStateError -> LoadingState.Error(error)

        is LoadStateNotLoading -> LoadingState.Done
    }

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun <T : Any> StateView(
    pagingItems: LazyPagingItems<T>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullRefreshState(refreshing = refreshing, onRefresh = {
        refreshing = true
        pagingItems.refresh()
    })
    val refresh = pagingItems.loadState.refresh
    LaunchedEffect(key1 = refreshing, key2 = refresh) {
        // 增加延时，确保真正进入刷新状态
        delay(REFRESH_AFTER)
        if (refreshing) {
            // 刷新结束或者当前没有内容时停止刷新，如果没有内容会使用列表刷新控件
            if (refresh !is LoadStateLoading || pagingItems.itemCount == 0) refreshing = false
        }
    }
    val loadState by produceState<androidx.paging.LoadState?>(null, key1 = refresh) {
        delay(100)
        value = refresh
    }
    Box(modifier = modifier.pullRefresh(refreshState)) {
        StateViewInternal(state = loadState.toLoadingState(), refresh = {
            pagingItems.refresh()
        }, pagingItems.itemCount, content)
        PullRefreshIndicator(refreshing, refreshState, Modifier.align(Alignment.TopCenter))
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun <T> StateView(
    handler: LoadingHandler<T>,
    extraRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable (T & Any) -> Unit
) {
    val state by handler.state.collectAsState()
    val data by handler.data.collectAsState()
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullRefreshState(refreshing = refreshing, onRefresh = {
        refreshing = true
        handler.refresh()
        extraRefresh()
    })
    LaunchedEffect(key1 = refreshing, key2 = state) {
        delay(REFRESH_AFTER)
        if (refreshing && state !is LoadingState.Loading) refreshing = false
    }
    Box(modifier = modifier.pullRefresh(refreshState)) {
        StateViewInternal(state, refresh = {
            handler.refresh()
            extraRefresh()
        }, 1) {
            data?.let {
                content(it)
            }
        }
        PullRefreshIndicator(refreshing, refreshState, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun StateViewInternal(
    state: LoadingState?,
    refresh: () -> Unit,
    itemCount: Int,
    content: @Composable () -> Unit
) {
    when (state) {
        null -> CenterBox {
            CircularProgressIndicator()
        }

        is LoadingState.Loading -> if (itemCount == 0) {
            CenterBox {
                CircularProgressIndicator()
            }
        } else {
            content()
        }

        is LoadingState.Error -> CenterBox {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ExceptionView(state.e)
                Button({
                    refresh()
                }, modifier = Modifier) {
                    Text(stringResource(Res.string.refresh))
                }
            }
        }

        is LoadingState.Done -> if (itemCount == 0) {
            CenterBox {
                Text(text = stringResource(Res.string.no_content_yet))
            }
        } else {
            content()
        }
    }
}

@Composable
fun CenterBox(content: @Composable () -> Unit) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState), contentAlignment = Alignment.Center) {
        content()
    }
}

fun <T : Identifiable> LazyListScope.nestedStateView(
    items: LazyPagingItems<T>,
    content: @Composable (T?, Int) -> Unit
) {
    if (items.loadState.refresh is LoadStateNotLoading && items.itemCount == 0) {
        item {
            Text(
                stringResource(Res.string.no_content_yet),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
    when (val refreshState = items.loadState.refresh.toLoadingState()) {
        is LoadingState.Loading -> {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        is LoadingState.Error -> {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ExceptionView(refreshState.e)
                    Button({
                        items.refresh()
                    }, modifier = Modifier) {
                        Text("Refresh")
                    }
                }
            }
        }

        else -> {}
    }
    nestedStateList(items, content)
    when (items.loadState.refresh.toLoadingState()) {
        is LoadingState.Loading, is LoadingState.Error -> {}

        else -> {
            if (items.loadState.append is LoadStateLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

private fun <T : Identifiable> LazyListScope.nestedStateList(
    items: LazyPagingItems<T>,
    content: @Composable (T?, Int) -> Unit
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
    content: @Composable (T) -> Unit
) {
    val data by handler.data.collectAsState()
    val state by handler.state.collectAsState()
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
                    globalDialogState.showErrorState(localState.e)
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
