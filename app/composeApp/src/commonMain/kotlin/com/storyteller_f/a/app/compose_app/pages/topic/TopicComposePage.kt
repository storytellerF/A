package com.storyteller_f.a.app.compose_app.pages.topic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.storyteller_f.a.app.compose_app.*
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.compontents.TopicContentField
import com.storyteller_f.a.app.compose_app.model.OnTopicCreated
import com.storyteller_f.a.app.compose_app.model.getMarkdownMediasViewModel
import com.storyteller_f.a.app.compose_app.pages.community.getCommunityFont
import com.storyteller_f.a.app.compose_app.ui.theme.AppTheme
import com.storyteller_f.a.client.core.createNewTopic
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun TopicComposePage(
    objectType: ObjectType,
    objectId: PrimaryKey,
    enableExperimental: Boolean,
    privateRoomId: PrimaryKey?,
    communityId: PrimaryKey?,
    backPrePage: () -> Unit
) {
    val typography = communityId?.let {
        getCommunityFont(
            it
        )
    }
    AppTheme(
        typography = typography ?: MaterialTheme.typography
    ) {
        val userSessionManager = LocalSessionManager.current
        val myInfo by userSessionManager.sessionModel.userHandler.data.collectAsState()
        val user = myInfo
        user?.let {
            TopicComposeScaffold(
                objectType,
                objectId,
                if (privateRoomId != null) {
                    ObjectTuple(privateRoomId, ObjectType.ROOM)
                } else {
                    ObjectTuple(
                        it.id,
                        ObjectType.USER
                    )
                },
                backPrePage,
                enableExperimental
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TopicComposeScaffold(
    objectType: ObjectType,
    objectId: PrimaryKey,
    mediaTarget: ObjectTuple,
    backPrePage: () -> Unit,
    enableExperimental: Boolean
) {
    var input by remember {
        mutableStateOf("")
    }
    var showSheet by remember {
        mutableStateOf(false)
    }
    Scaffold(topBar = {
        TopAppBar({
        }, navigationIcon = {
            if (enableExperimental) {
                IconButton(onClick = {
                    showSheet = true
                }) {
                    Icon(Icons.Filled.PermMedia, contentDescription = null)
                }
            }
        }, actions = {
            TopicComposeSubmitButton(input, objectType, objectId) {
                input = ""
                backPrePage()
            }
        })
    }) { paddingValues ->
        val direction = LocalLayoutDirection.current
        Column(
            modifier = Modifier.padding(
                top = paddingValues.calculateTopPadding(),
                start = paddingValues.calculateStartPadding(direction),
                end = paddingValues.calculateRightPadding(direction)
            )
        ) {
            TopicComposeInternal(input, enableExperimental, mediaTarget) {
                input = it
            }
        }
    }
    val sheetState = rememberModalBottomSheetState()
    MediaPicker(showSheet, sheetState, mediaTarget, onClickItems = { info ->
        insertContent(info.first(), {
            input = it
        }, input)
    }) {
        showSheet = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicComposeInternal(
    input: String,
    enableExperimental: Boolean,
    mediaTarget: ObjectTuple,
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
    HorizontalPager(pagerState, key = tabs::get) { index ->
        when {
            index == 0 && !enableExperimental -> RichEditTopicPage(input, state, updateInput)
            index == 1 -> PreviewTopicPage(input, mediaTarget)
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
    val sessionManager = LocalSessionManager.current
    val globalDialogController = LocalGlobalDialog.current
    IconButton({
        val finalInput = input.trim()
        if (finalInput.isNotEmpty()) {
            scope.launch {
                globalDialogController.useResult {
                    sessionManager.createNewTopic(objectType, objectId, finalInput)
                }.onSuccess { info ->
                    bus.emit(OnTopicCreated(info))
                    backPrePage()
                }
            }
        }
    }) {
        Icon(imageVector = Icons.Default.Check, "submit")
    }
}

@Composable
fun PreviewTopicPage(input: String, objectTuple: ObjectTuple) {
    val markdownMediasViewModel =
        getMarkdownMediasViewModel(input, objectTuple)
    val list by markdownMediasViewModel.handler.data.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp)) {
        item {
            TopicContentField(
                TopicInfo.EMPTY.copy(content = TopicContent.Plain(input, list.orEmpty().toImmutableList())),
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
