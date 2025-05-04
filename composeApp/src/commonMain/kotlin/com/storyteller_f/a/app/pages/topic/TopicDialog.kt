package com.storyteller_f.a.app.pages.topic

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.copy
import a.composeapp.generated.resources.snapshot
import a.composeapp.generated.resources.success
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToasterState
import com.storyteller_f.a.app.*
import com.storyteller_f.a.app.compontents.*
import com.storyteller_f.a.app.model.OnTopicChanged
import com.storyteller_f.a.app.model.createUserViewModel
import com.storyteller_f.a.app.pages.community.CommunityRefCell
import com.storyteller_f.a.app.pages.room.RoomRefCell
import com.storyteller_f.a.app.pages.user.UserCell
import com.storyteller_f.a.app.service.buildGPT
import com.storyteller_f.a.app.service.buildTranslatePrompt
import com.storyteller_f.a.app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.app.utils.getCurrentLanguage
import com.storyteller_f.a.app.utils.setText
import com.storyteller_f.a.client_lib.SignInViewModel
import com.storyteller_f.a.client_lib.getTopicSnapshot
import com.storyteller_f.a.client_lib.pinTopic
import com.storyteller_f.a.client_lib.unpinTopic
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.GPTOutput
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.formatTime
import com.strabled.composepreferences.getPreference
import io.github.aakira.napier.Napier
import io.ktor.client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicDialog(topicInfo: TopicInfo?, showDialog: Boolean, dismiss: () -> Unit) {
    if (topicInfo != null && showDialog) {
        BasicAlertDialog({
            dismiss()
        }) {
            val author = topicInfo.author
            val authorViewModel = createUserViewModel(author)
            val authorInfo by authorViewModel.handler.data.collectAsState()

            TopicDialogInternal(topicInfo, authorInfo, dismiss)
        }
    }
}

@Composable
fun TopicDialogInternal(topicInfo: TopicInfo, authorInfo: UserInfo?, dismissDialog: () -> Unit) {
    DialogContainer {
        UserCell(authorInfo, true)
        Text("pub: ${topicInfo.createdTime.formatTime()}")

        when (topicInfo.rootType) {
            ObjectType.COMMUNITY ->
                CommunityRefCell(topicInfo.rootId)

            ObjectType.ROOM ->
                RoomRefCell(topicInfo.rootId)

            else -> {}
        }
        TopicDialogMenuList(topicInfo, dismissDialog)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicDialogMenuList(
    topicInfo: TopicInfo,
    dismissDialog: () -> Unit,
) {
    val toasterState = LocalToaster.current
    val content = topicInfo.content
    Column {
        TopicMenuList(
            content,
            toasterState,
            topicInfo,
            dismissDialog,
        )
        var showSheet by remember {
            mutableStateOf(false)
        }
        val sheetState = rememberModalBottomSheetState()
        ButtonNav(MaterialSymbolsOutlined.Translate, "Translate") {
            if (content is TopicContent.Plain) {
                showSheet = true
            } else {
                toasterState.show("can't translate", duration = 1.seconds)
            }
        }
        TopicTranslateSheet(showSheet, sheetState, topicInfo) {
            showSheet = false
        }
    }
}

@Composable
private fun TopicMenuList(
    content: TopicContent,
    toasterState: ToasterState,
    topicInfo: TopicInfo,
    dismissDialog: () -> Unit,
) {
    val clipboardManager = LocalClipboard.current
    val alreadyLoginIn by SignInViewModel.isAlreadySignUp.collectAsState(false)
    val appNav = LocalAppNav.current
    val scope = rememberCoroutineScope()
    val client = LocalClient.current
    ButtonNav(Icons.Default.ContentCopy, stringResource(Res.string.copy)) {
        scope.launch {
            if (content is TopicContent.Plain) {
                clipboardManager.setText(content.plain)
            } else {
                toasterState.show("failed", duration = 1.seconds)
            }
        }
    }
    if (alreadyLoginIn) {
        ButtonNav(Icons.Default.PictureAsPdf, stringResource(Res.string.snapshot)) {
            scope.launch {
                globalDialogState.use {
                    client.getTopicSnapshot(topicInfo.id)
                    toasterState.show(getString(Res.string.success), duration = 1.seconds)
                }
            }
        }
        ButtonNav(Icons.Default.Add, "Add") {
            dismissDialog()
            appNav.gotoTopicCompose(
                ObjectType.TOPIC,
                topicInfo.id,
                true,
                topicInfo.rootId.takeIf { topicInfo.rootType == ObjectType.ROOM && topicInfo.isPrivate }
            )
        }
    }

    ButtonNav(
        if (topicInfo.isPin) MaterialSymbolsOutlined.KeepOff else MaterialSymbolsOutlined.Keep,
        if (topicInfo.isPin) "Unpin" else "Pin"
    ) {
        scope.launch {
            pinOrUnpinTopic(topicInfo, client).onSuccess {
                dismissDialog()
            }
        }
    }
}

suspend fun pinOrUnpinTopic(
    topicInfo: TopicInfo,
    client: HttpClient
): Result<TopicInfo> {
    return globalDialogState.use {
        if (topicInfo.isPin) {
            client.unpinTopic(topicInfo.id)
        } else {
            client.pinTopic(topicInfo.id)
        }.getOrThrow()
    }
}

@Composable
fun TopicDropdownMenu(expanded: Boolean, topicInfo: TopicInfo, onDismissRequest: () -> Unit) {
    val scope = rememberCoroutineScope()
    val client = LocalClient.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        val title = if (topicInfo.isPin) "Unpin" else "Pin"
        DropdownMenuItem(
            leadingIcon = {
                CustomIcon(
                    IconRes.Font(
                        if (topicInfo.isPin) MaterialSymbolsOutlined.KeepOff else MaterialSymbolsOutlined.Keep
                    )
                )
            },
            text = { Text(title) },
            onClick = {
                scope.launch {
                    pinOrUnpinTopic(topicInfo, client).onSuccess {
                        onDismissRequest()
                        bus.emit(OnTopicChanged(it))
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicTranslateSheet(
    showSheet: Boolean,
    sheetState: SheetState,
    topicInfo: TopicInfo,
    hideSheet: () -> Unit
) {
    BaseSheet(showSheet, sheetState, hideSheet) {
        SheetContainer {
            val preferenceData: StateFlow<String> by getPreference("gpt_model")
            val currentModel by preferenceData.collectAsState()
            val content = topicInfo.content
            Box(modifier = Modifier.height(200.dp).padding(horizontal = 20.dp).fillMaxWidth()) {
                if (content is TopicContent.Plain) {
                    val result by produceState<Result<Flow<GPTOutput>>?>(null, content, currentModel) {
                        value = withContext(Dispatchers.IO) {
                            val (prompt, stopWord) = buildTranslatePrompt(
                                content.plain,
                                getCurrentLanguage(),
                                currentModel
                            )
                            Napier.i(tag = "gpt") {
                                "prompt $prompt"
                            }
                            buildGPT().generate(currentModel, prompt, stopWord)
                        }
                    }
                    TopicTranslateSheetInternal(result, topicInfo, content)
                } else {
                    Text("can't process", modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun BoxScope.TopicTranslateSheetInternal(
    result: Result<Flow<GPTOutput>>?,
    topicInfo: TopicInfo,
    content: TopicContent.Plain
) {
    when {
        result == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

        result.isSuccess -> {
            var output by remember {
                mutableStateOf("")
            }
            val scope = rememberCoroutineScope()
            val outputFlow = result.getOrThrow()
            DisposableEffect(outputFlow) {
                val job = scope.launch {
                    outputFlow.onCompletion {
                        Napier.i(tag = "gpt") {
                            "complete"
                        }
                    }.collect {
                        output += it.text
                    }
                }
                onDispose {
                    Napier.i(tag = "gpt") {
                        "dispose"
                    }
                    job.cancel()
                }
            }
            TopicContentField(topicInfo.copy(content = content.copy(plain = output)))
        }

        else ->
            result.exceptionOrNull()?.let { ExceptionView(it, modifier = Modifier.Companion.align(Alignment.Center)) }
    }
}
