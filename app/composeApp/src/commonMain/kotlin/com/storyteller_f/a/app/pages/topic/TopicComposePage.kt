package com.storyteller_f.a.app.pages.topic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Typography
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.LocalUserInfo
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.common.MarkdownMediasViewModel
import com.storyteller_f.a.app.common.OnTopicCreated
import com.storyteller_f.a.app.common.createRoomViewModel
import com.storyteller_f.a.app.common.getMarkdownMediasViewModel
import com.storyteller_f.a.app.components.AppTopicContentView
import com.storyteller_f.a.app.core.components.CustomAlertDialog
import com.storyteller_f.a.app.core.components.emitEvent
import com.storyteller_f.a.app.core.components.rememberAlertDialogController
import com.storyteller_f.a.app.core.components.request
import com.storyteller_f.a.app.core.components.safeArea
import com.storyteller_f.a.app.edit
import com.storyteller_f.a.app.pages.community.getCommunityFont
import com.storyteller_f.a.app.pages.community.getFontFamily
import com.storyteller_f.a.app.pages.room.RoomSendButton
import com.storyteller_f.a.app.preview
import com.storyteller_f.a.app.raw
import com.storyteller_f.a.app.submit
import com.storyteller_f.a.app.toggle_bold
import com.storyteller_f.a.app.toggle_code_span
import com.storyteller_f.a.app.toggle_italic
import com.storyteller_f.a.app.toggle_line_through
import com.storyteller_f.a.app.toggle_ordered_list
import com.storyteller_f.a.app.toggle_underline
import com.storyteller_f.a.app.toggle_unordered_list
import com.storyteller_f.a.app.ui.theme.AppTheme
import com.storyteller_f.a.client.core.createTopic
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

sealed interface TopicComposeData {
    fun getMediaTarget(): ObjectTuple?
    fun getParent(): ObjectTuple

    data class PublicRoom(
        val roomId: PrimaryKey,
        val communityId: PrimaryKey,
        val parentTuple: ObjectTuple
    ) : TopicComposeData {
        override fun getMediaTarget(): ObjectTuple? {
            return null
        }

        override fun getParent() = parentTuple
    }

    data class PrivateRoom(
        val roomId: PrimaryKey,
        val parentTuple: ObjectTuple
    ) : TopicComposeData {
        override fun getMediaTarget(): ObjectTuple {
            return roomId ob ObjectType.ROOM
        }

        override fun getParent() = parentTuple
    }

    data class User(
        val uid: PrimaryKey,
        val objectTuple: ObjectTuple
    ) : TopicComposeData {
        override fun getMediaTarget(): ObjectTuple? {
            return null
        }

        override fun getParent() = objectTuple
    }

    data class Community(val communityId: PrimaryKey, val objectTuple: ObjectTuple) :
        TopicComposeData {
        override fun getMediaTarget(): ObjectTuple? {
            return null
        }

        override fun getParent(): ObjectTuple {
            return objectTuple
        }
    }
}

@Composable
fun TopicComposePage(
    data: TopicComposeData,
    backPrePage: () -> Unit
) {
    val myInfo = LocalUserInfo.current ?: return
    val typography = getCommunityTypography(data)
    AppTheme(
        typography = typography ?: MaterialTheme.typography
    ) {
        TopicComposeScaffold(
            data,
            data.getMediaTarget() ?: (myInfo.id ob ObjectType.USER),
            backPrePage,
        )
    }
}

@Composable
private fun getCommunityTypography(data: TopicComposeData): Typography? =
    (data as? TopicComposeData.PublicRoom)?.communityId?.let {
        getCommunityFont(it)
    }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TopicComposeScaffold(
    data: TopicComposeData,
    mediaTarget: ObjectTuple,
    backPrePage: () -> Unit,
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
            IconButton(onClick = {
                showSheet = true
            }) {
                Icon(Icons.Filled.PermMedia, contentDescription = null)
            }
        }, actions = {
            TopicComposeSubmitButton(input, data) {
                input = ""
                backPrePage()
            }
        })
    }) { paddingValues ->
        val direction = LocalLayoutDirection.current
        Column(modifier = Modifier.safeArea(paddingValues, direction)) {
            TopicComposeInternal(input, mediaTarget, data) {
                input = it
            }
        }
    }
    val sheetState = rememberModalBottomSheetState()
    FilePicker(showSheet, sheetState, mediaTarget, onClickItems = { info ->
        insertContent(info.first(), input) {
            input = it
        }
    }) {
        showSheet = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicComposeInternal(
    input: String,
    mediaTarget: ObjectTuple,
    data: TopicComposeData,
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
        when (index) {
            0 -> RichEditTopicPage(input, state, data, updateInput)
            1 -> PreviewTopicPage(input, mediaTarget)
            else -> EditTopicPage(input, data) {
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
fun RichEditTopicPage(
    input: String,
    state: RichTextState,
    data: TopicComposeData,
    updateInput: (String) -> Unit
) {
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
    val fontFamily = getFontFamily(data)
    val textStyle = LocalTextStyle.current
    Column(modifier = Modifier.navigationBarsPadding()) {
        TopicComposeToolbar(currentSpanStyle, state)
        BasicRichTextEditor(
            state = state,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
            textStyle = textStyle.merge(color = LocalContentColor.current, fontFamily = fontFamily)
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
            Icon(Icons.Default.FormatBold, stringResource(Res.string.toggle_bold))
        }
        IconToggleButton(currentSpanStyle.fontStyle == FontStyle.Italic, {
            state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
        }) {
            Icon(Icons.Default.FormatItalic, stringResource(Res.string.toggle_italic))
        }
        IconToggleButton(currentSpanStyle.textDecoration == TextDecoration.Underline, {
            state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
        }) {
            Icon(Icons.Default.FormatUnderlined, stringResource(Res.string.toggle_underline))
        }
        IconToggleButton(currentSpanStyle.textDecoration == TextDecoration.LineThrough, {
            state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
        }) {
            Icon(Icons.Default.FormatStrikethrough, stringResource(Res.string.toggle_line_through))
        }
        VerticalDivider(modifier = Modifier.height(20.dp).align(Alignment.CenterVertically))
        IconToggleButton(state.isOrderedList, {
            state.toggleOrderedList()
        }) {
            Icon(Icons.Default.FormatListNumbered, stringResource(Res.string.toggle_ordered_list))
        }
        IconToggleButton(state.isUnorderedList, {
            state.toggleUnorderedList()
        }) {
            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(Res.string.toggle_unordered_list))
        }
        VerticalDivider(modifier = Modifier.height(20.dp).align(Alignment.CenterVertically))
        IconToggleButton(state.isCodeSpan, {
            state.toggleCodeSpan()
        }) {
            Icon(Icons.Default.Code, stringResource(Res.string.toggle_code_span))
        }
    }
}

@Composable
private fun TopicComposeSubmitButton(
    input: String,
    data: TopicComposeData,
    backPrePage: () -> Unit
) {
    val alertDialogController = rememberAlertDialogController()
    CustomAlertDialog(alertDialogController, {
        alertDialogController.close()
    }) {
    }
    if (data is TopicComposeData.PublicRoom) {
        val roomViewModel = createRoomViewModel(data.roomId)
        val roomData by roomViewModel.handler.data.collectAsState()
        roomData?.let {
            RoomSendButton(
                input,
                it,
                data.parentTuple,
                alertDialogController,
            ) {
            }
        }
        return
    }
    if (data is TopicComposeData.PrivateRoom) {
        val roomViewModel = createRoomViewModel(data.roomId)
        val roomData by roomViewModel.handler.data.collectAsState()
        roomData?.let {
            RoomSendButton(
                input,
                it,
                data.parentTuple,
                alertDialogController,
            ) {}
        }
        return
    }
    val (objectId, objectType) = data.getParent()
    val scope = rememberCoroutineScope()
    val globalDialogController = LocalGlobalDialog.current
    IconButton({
        val finalInput = input.trim()
        if (finalInput.isNotEmpty()) {
            scope.launch {
                globalDialogController.useResult {
                    request {
                        createTopic(objectType, objectId, finalInput)
                    }
                }.onSuccess { info ->
                    globalDialogController.emitEvent(OnTopicCreated(info))
                    backPrePage()
                }
            }
        }
    }) {
        Icon(imageVector = Icons.AutoMirrored.Filled.Send, stringResource(Res.string.submit))
    }
}

@Composable
fun PreviewTopicPage(input: String, objectTuple: ObjectTuple) {
    val markdownMediasViewModel =
        getMarkdownMediasViewModel(input, objectTuple)
    PreviewTopicInternal(markdownMediasViewModel, input)
}

@Composable
private fun PreviewTopicInternal(
    markdownMediasViewModel: MarkdownMediasViewModel,
    input: String
) {
    val list by markdownMediasViewModel.handler.data.collectAsState()
    val topicInfo = TopicInfo.EMPTY.copy(
        content = TopicContent.Plain(
            input,
            list.orEmpty().toImmutableList()
        )
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp)
    ) {
        item {
            AppTopicContentView(topicInfo)
        }
    }
}

@Composable
fun EditTopicPage(input: String, data: TopicComposeData, updateInput: (String) -> Unit) {
    Box(modifier = Modifier.navigationBarsPadding()) {
        val fontFamily = getFontFamily(data)
        val textStyle = LocalTextStyle.current
        BasicTextField(
            input,
            updateInput,
            modifier = Modifier.fillMaxSize().padding(20.dp),
            textStyle = textStyle.merge(color = LocalContentColor.current, fontFamily = fontFamily)
        )
    }
}

@Composable
private fun getFontFamily(data: TopicComposeData): FontFamily? {
    return (data as? TopicComposeData.PublicRoom)?.communityId?.let {
        getFontFamily(it).value
    }
}
