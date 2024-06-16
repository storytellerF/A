package com.storyteller_f.a.app.common

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
import androidx.compose.ui.unit.dp
import app.cash.paging.LoadState
import app.cash.paging.LoadStateError
import app.cash.paging.LoadStateLoading
import app.cash.paging.LoadStateNotLoading
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.itemContentType
import app.cash.paging.compose.itemKey
import com.storyteller_f.shared.model.Identifiable
import com.storyteller_f.a.client_lib.LoadingHandler
import com.storyteller_f.a.client_lib.LoadingState
import kotlinx.coroutines.delay


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
        //增加延时，确保真正进入刷新状态
        delay(REFRESH_AFTER)
        if (refreshing && refresh !is LoadStateLoading) refreshing = false
    }
    Box(modifier = modifier.pullRefresh(refreshState)) {
        StateView(state = refresh.toLoadingState(pagingItems.itemCount), refresh = {
            pagingItems.refresh()
        }, content)
        PullRefreshIndicator(refreshing, refreshState, Modifier.align(Alignment.TopCenter))
    }
}

const val REFRESH_AFTER = 300L


private fun LoadState?.toLoadingState(count: Int) = when (this) {
    null -> null

    is LoadStateLoading -> LoadingState.Loading("loading")

    is LoadStateError -> LoadingState.Error(error)

    is LoadStateNotLoading -> LoadingState.Done(count)
}

@Composable
fun StateView(state: LoadingState?, refresh: () -> Unit, content: @Composable () -> Unit) {
    when (state) {
        null -> CenterBox {
            CircularProgressIndicator()
        }

        is LoadingState.Loading -> CenterBox {
            CircularProgressIndicator()
        }

        is LoadingState.Error -> CenterBox {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = state.e.message.toString())
                Button({
                    refresh()
                }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Refresh")
                }
            }
        }

        is LoadingState.Done -> if (state.itemCount == 0) CenterBox {
            Text(text = "empty")
        } else content()
    }
}

@Composable
fun CenterBox(content: @Composable () -> Unit) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState), contentAlignment = Alignment.Center) {
        content()
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun <T> StateView(handler: LoadingHandler<T?>, content: @Composable (T) -> Unit) {
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
    Box(modifier = Modifier.pullRefresh(refreshState)) {
        StateView(state, refresh = {
            handler.refresh()
        }) {
            data?.let {
                content(it)
            }
        }
        PullRefreshIndicator(refreshing, refreshState, Modifier.align(Alignment.TopCenter))
    }
}

fun <T : Identifiable> LazyListScope.nestedStateView(items: LazyPagingItems<T>, content: @Composable (T?) -> Unit) {
    when (val refreshState = items.loadState.refresh) {
        is LoadStateLoading -> {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                    CircularProgressIndicator()
                }
            }
        }

        is LoadStateError -> {
            item {
                Column(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                    Text(text = refreshState.error.message.toString())
                    Button({
                        items.refresh()
                    }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Refresh")
                    }
                }
            }
        }

        is LoadStateNotLoading -> {
            items(items.itemCount, key = items.itemKey {
                it.id
            }, contentType = items.itemContentType()) {
                content(items[it])
            }
            if (items.loadState.append is LoadStateLoading)
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                        CircularProgressIndicator()
                    }
                }
        }
    }
}

