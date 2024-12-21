package com.storyteller_f.a.app.topic

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.edit
import a.composeapp.generated.resources.preview
import a.composeapp.generated.resources.raw
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.StateView
import com.storyteller_f.a.app.common.getOrCreateCollection
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.model.MediaListViewModel
import com.storyteller_f.a.app.model.createMediaListViewModel
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.createNewTopic
import com.storyteller_f.a.client_lib.upload
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.extension
import io.github.vinceglb.filekit.core.pickFile
import kotbase.MutableDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
fun TopicComposePage(
    objectType: ObjectType,
    objectId: PrimaryKey,
    enableExperimental: Boolean,
    privateRoomId: PrimaryKey?,
    backPrePage: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val user by LoginViewModel.user.collectAsState()
    user?.let {
        TopicComposeScaffold(
            it,
            scope,
            drawerState,
            objectType,
            objectId,
            backPrePage,
            enableExperimental,
            privateRoomId
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TopicComposeScaffold(
    user: UserInfo,
    scope: CoroutineScope,
    drawerState: DrawerState,
    objectType: ObjectType,
    objectId: PrimaryKey,
    backPrePage: () -> Unit,
    enableExperimental: Boolean,
    privateRoomId: PrimaryKey?
) {
    var input by remember {
        mutableStateOf("")
    }
    val list = createMediaListViewModel(privateRoomId, user.id)
    ModalNavigationDrawer({
        TopicComposeDrawer(scope, list, input) {
            input = it
        }
    }, drawerState = drawerState, modifier = Modifier.fillMaxWidth(), gesturesEnabled = enableExperimental) {
        Scaffold(topBar = {
            TopAppBar({
            }, navigationIcon = {
                if (enableExperimental) {
                    IconButton(onClick = {
                        scope.launch {
                            drawerState.open()
                        }
                    }) {
                        Icon(Icons.Filled.Menu, contentDescription = null)
                    }
                }
            }, actions = {
                TopicComposeSubmitButton(input, objectType, objectId) {
                    input = ""
                    backPrePage()
                }
            })
        }) { paddingValues ->
            Column(modifier = Modifier.padding(top = paddingValues.calculateTopPadding())) {
                TopicComposeInternal(input, list, enableExperimental) {
                    input = it
                }
            }
        }
    }
}

@Composable
private fun TopicComposeDrawer(
    scope: CoroutineScope,
    list: MediaListViewModel,
    input: String,
    updateInput: (String) -> Unit
) {
    val toasterState = rememberToasterState()
    val my by LoginViewModel.user.collectAsState()
    Toaster(toasterState)
    ModalDrawerSheet {
        Row {
            IconButton({
                scope.launch {
                    globalDialogState.use {
                        val id = my?.id
                        if (id != null) {
                            val f = FileKit.pickFile()
                            if (f != null) {
                                val size = f.getSize()
                                if (size != null && size <= 100 * 1024 * 1024) {
                                    client.upload(f.readBytes(), f.name, f.extension, id, ObjectType.USER)
                                } else {
                                    toasterState.show("size is null or size too big", duration = 1.seconds)
                                }
                            }
                        }
                    }
                }
            }) {
                Icon(Icons.Default.UploadFile, "upload file")
            }
            IconButton({
                list.handler.refresh()
            }) {
                Icon(Icons.Default.Refresh, "refresh file")
            }
        }
        StateView(list.handler) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(it.data) {
                    NavigationDrawerItem({
                        Text(it.item.name)
                    }, false, {
                        updateInput(
                            """$input
```object
{
    "contentType": "application/pdf",
    "name": "${it.item.name}"
}
```"""
                        )
                    }, icon = {
                        AsyncImage(it.url, it.item.name, modifier = Modifier.size(40.dp))
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicComposeInternal(
    input: String,
    mediaListViewModel: MediaListViewModel,
    enableExperimental: Boolean,
    updateInput: (String) -> Unit
) {
    val pagerState = rememberPagerState {
        3
    }
    val state = rememberRichTextState()
    val tabs = listOf(
        stringResource(Res.string.edit),
        stringResource(Res.string.preview),
        stringResource(Res.string.raw)
    )
    val selected = pagerState.currentPage
    val coroutineScope = rememberCoroutineScope()

    PrimaryTabRow(selected) {
        tabs.forEachIndexed { i, e ->
            Tab(selected = selected == i, onClick = {
                coroutineScope.launch {
                    pagerState.scrollToPage(i)
                }
            }) {
                Text(text = e, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
    val list by mediaListViewModel.handler.data.collectAsState()
    HorizontalPager(pagerState, key = tabs::get) { index ->
        when {
            index == 0 && !enableExperimental -> RichEditTopicPage(input, state, updateInput)
            index == 1 -> PreviewTopicPage(input, list?.data)
            else -> EditTopicPage(input) {
                Napier.i {
                    "markdown update1 $it"
                }
                updateInput(it)
                state.setMarkdown(it)
            }
        }
    }
}

@Composable
fun RichEditTopicPage(input: String, state: RichTextState, updateInput: (String) -> Unit) {
    LaunchedEffect(state.annotatedString) {
        val markdown = state.toMarkdown()
        Napier.i {
            "markdown effect $markdown"
        }
        if (input != markdown) {
            Napier.i {
                "markdown edit update input $markdown"
            }
            updateInput(markdown)
        }
    }
    LaunchedEffect(input) {
        if (state.toMarkdown() != input) {
            Napier.i {
                "markdown edit update $input"
            }
            state.setMarkdown(input)
        }
    }
    val currentSpanStyle = state.currentSpanStyle
    Column(modifier = Modifier.navigationBarsPadding()) {
        TopicComposeToolbar(currentSpanStyle, state)
        BasicRichTextEditor(
            state = state,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TopicComposeToolbar(
    currentSpanStyle: SpanStyle,
    state: RichTextState
) {
    FlowRow {
        IconToggleButton(currentSpanStyle.fontWeight == FontWeight.Bold, {
            state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
        }) {
            Icon(Icons.Default.FormatBold, "toggle bold")
        }
        IconToggleButton(currentSpanStyle.fontStyle == FontStyle.Italic, {
            state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
        }) {
            Icon(Icons.Default.FormatItalic, "toggle italic")
        }
        IconToggleButton(currentSpanStyle.textDecoration == TextDecoration.Underline, {
            state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
        }) {
            Icon(Icons.Default.FormatUnderlined, "toggle underline")
        }
        IconToggleButton(currentSpanStyle.textDecoration == TextDecoration.LineThrough, {
            state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
        }) {
            Icon(Icons.Default.FormatStrikethrough, "toggle line through")
        }
        VerticalDivider(modifier = Modifier.height(20.dp).align(Alignment.CenterVertically))
        IconToggleButton(state.isOrderedList, {
            state.toggleOrderedList()
        }) {
            Icon(Icons.Default.FormatListNumbered, "toggle ordered list")
        }
        IconToggleButton(state.isUnorderedList, {
            state.toggleUnorderedList()
        }) {
            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, "toggle unordered list")
        }
        VerticalDivider(modifier = Modifier.height(20.dp).align(Alignment.CenterVertically))
        IconToggleButton(state.isCodeSpan, {
            state.toggleCodeSpan()
        }) {
            Icon(Icons.Default.Code, "toggle code span")
        }
    }
}

@Composable
private fun TopicComposeSubmitButton(
    input: String,
    objectType: ObjectType,
    objectId: PrimaryKey,
    backPrePage: () -> Unit
) {
    val scope = rememberCoroutineScope()

    IconButton({
        val finalInput = input.trim()
        if (finalInput.isNotEmpty()) {
            scope.launch {
                globalDialogState.use {
                    val info = client.createNewTopic(objectType, objectId, finalInput).getOrThrow()
                    getOrCreateCollection("topics${info.parentId}").save(
                        MutableDocument(
                            info.id.toString(),
                            Json.encodeToString(info)
                        )
                    )
                    backPrePage()
                }
            }
        }
    }) {
        Icon(imageVector = Icons.Default.Check, "submit")
    }
}

@Composable
fun PreviewTopicPage(input: String, res: List<MediaInfo>?) {
    LazyColumn(modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp)) {
        item {
            TopicContentField(
                TopicInfo.EMPTY.copy(content = TopicContent.Plain(input, res.orEmpty())),
                showHeadline = false,
            )
        }
    }
}

@Composable
fun EditTopicPage(input: String, updateInput: (String) -> Unit) {
    Box(modifier = Modifier.navigationBarsPadding()) {
        BasicTextField(input, updateInput, modifier = Modifier.fillMaxSize().padding(20.dp))
    }
}
