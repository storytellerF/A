package com.storyteller_f.a.app.topic

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.edit
import a.composeapp.generated.resources.preview
import a.composeapp.generated.resources.raw
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.getOrCreateCollection
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.client_lib.createNewTopic
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.client.call.*
import kotbase.MutableDocument
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TopicComposePage(objectType: ObjectType, objectId: PrimaryKey, backPrePage: () -> Unit) {
    var input by remember {
        mutableStateOf("")
    }

    Scaffold(topBar = {
        TopAppBar({
        }, actions = {
            TopicComposeSubmitButton(input, objectType, objectId, backPrePage)
        })
    }) {
        Column(modifier = Modifier.padding(top = it.calculateTopPadding())) {
            TopicComposeInternal(input) {
                input = it
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicComposeInternal(
    input: String,
    updateInput: (String) -> Unit
) {
    val pagerState = rememberPagerState {
        3
    }
    val state = rememberRichTextState()
    val tabs = listOf(stringResource(Res.string.edit), stringResource(Res.string.preview), stringResource(Res.string.raw))
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
        if (index == 0) {
            RichEditTopicPage(state, updateInput)
        } else if (index == 1) {
            PreviewTopicPage(input)
        } else {
            EditTopicPage(input) {
                updateInput(it)
                state.setMarkdown(it)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichEditTopicPage(state: RichTextState, updateInput: (String) -> Unit) {
    LaunchedEffect(state.annotatedString) {
        updateInput(state.toMarkdown())
    }
    val currentSpanStyle = state.currentSpanStyle
    Column(modifier = Modifier.navigationBarsPadding()) {
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
            VerticalDivider(modifier = Modifier.height(20.dp).align(androidx.compose.ui.Alignment.CenterVertically))
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
            VerticalDivider(modifier = Modifier.height(20.dp).align(androidx.compose.ui.Alignment.CenterVertically))
            IconToggleButton(state.isCodeSpan, {
                state.toggleCodeSpan()
            }) {
                Icon(Icons.Default.Code, "toggle code span")
            }
        }
        BasicRichTextEditor(
            state = state,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun TopicComposeSubmitButton(
    input: String,
    objectType: ObjectType,
    objectId: PrimaryKey,
    backPrePage: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    IconButton({
        val c = input.trim()
        if (c.isNotEmpty()) {
            coroutineScope.launch {
                try {
                    val info = client.createNewTopic(objectType, objectId, input).body<TopicInfo>()
                    getOrCreateCollection("topics${info.parentId}").save(
                        MutableDocument(
                            info.id.toString(),
                            Json.encodeToString(info)
                        )
                    )
                    backPrePage()
                } catch (e: Exception) {
                    globalDialogState.showError(e)
                }
            }
        }
    }) {
        Icon(imageVector = Icons.Default.Check, "submit")
    }
}

@Composable
fun PreviewTopicPage(input: String) {
    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        TopicContentField(TopicContent.Plain(input), modifier = Modifier.padding(20.dp))
    }
}

@Composable
fun EditTopicPage(input: String, updateInput: (String) -> Unit) {
    Box(modifier = Modifier.navigationBarsPadding()) {
        BasicTextField(input, updateInput, modifier = Modifier.fillMaxSize().padding(20.dp))
    }
}
