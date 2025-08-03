package com.storyteller_f.a.app.compose_app.pages.topic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.compose_app.LocalAppNav
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.LocalToaster
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.bus
import com.storyteller_f.a.app.compose_app.compontents.BaseSheet
import com.storyteller_f.a.app.compose_app.compontents.ButtonNav
import com.storyteller_f.a.app.compose_app.compontents.CustomIcon
import com.storyteller_f.a.app.compose_app.compontents.DialogContainer
import com.storyteller_f.a.app.compose_app.compontents.ExceptionView
import com.storyteller_f.a.app.compose_app.compontents.GlobalDialogController
import com.storyteller_f.a.app.compose_app.compontents.IconRes
import com.storyteller_f.a.app.compose_app.compontents.SheetContainer
import com.storyteller_f.a.app.compose_app.compontents.TopicContentField
import com.storyteller_f.a.app.compose_app.copy
import com.storyteller_f.a.app.compose_app.model.OnTopicChanged
import com.storyteller_f.a.app.compose_app.model.createUserViewModel
import com.storyteller_f.a.app.compose_app.pages.community.CommunityRefCell
import com.storyteller_f.a.app.compose_app.pages.room.RoomRefCell
import com.storyteller_f.a.app.compose_app.service.GPTOutput
import com.storyteller_f.a.app.compose_app.service.buildGPT
import com.storyteller_f.a.app.compose_app.service.buildTranslatePrompt
import com.storyteller_f.a.app.compose_app.snapshot
import com.storyteller_f.a.app.compose_app.success
import com.storyteller_f.a.app.compose_app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.app.compose_app.utils.getCurrentLanguage
import com.storyteller_f.a.app.compose_app.utils.setText
import com.storyteller_f.a.client.core.SessionManager
import com.storyteller_f.a.client.core.getTopicSnapshot
import com.storyteller_f.a.client.core.pinTopic
import com.storyteller_f.a.client.core.unpinTopic
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.formatTime
import com.strabled.composepreferences.getPreference
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicDialog(topicInfo: TopicInfo?, showDialog: Boolean, dismiss: () -> Unit) {
    if (topicInfo != null && showDialog) {
        BasicAlertDialog({
            dismiss()
        }) {
            TopicDialogInternal(topicInfo, dismiss)
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
fun TopicDialogInternal(topicInfo: TopicInfo, dismissDialog: () -> Unit) {
    val appNav = LocalAppNav.current
    DialogContainer {
        Text("pub: ${topicInfo.createdTime.formatTime()}")

        when (topicInfo.rootType) {
            ObjectType.COMMUNITY ->
                CommunityRefCell(topicInfo.rootId) {
                    dismissDialog()
                    appNav.gotoCommunity(topicInfo.rootId, false)
                }

            ObjectType.ROOM ->
                RoomRefCell(topicInfo.rootId) {
                    dismissDialog()
                    appNav.gotoRoom(topicInfo.rootId, false)
                }

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
            topicInfo,
            dismissDialog,
        )
        var showSheet by remember {
            mutableStateOf(false)
        }
        val sheetState = rememberModalBottomSheetState()
        ButtonNav(
            MaterialSymbolsOutlined.Translate,
            "Translate"
        ) {
            if (content is TopicContent.Plain) {
                showSheet = true
            } else {
                toasterState.showMessage("can't translate")
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
    topicInfo: TopicInfo,
    dismissDialog: () -> Unit,
) {
    val toast = LocalToaster.current
    val clipboardManager = LocalClipboard.current
    val userSessionManager = LocalSessionManager.current
    val alreadyLoginIn by userSessionManager.isAlreadySignUp.collectAsState()
    val appNav = LocalAppNav.current
    val scope = rememberCoroutineScope()
    ButtonNav(
        Icons.Default.ContentCopy,
        stringResource(Res.string.copy)
    ) {
        scope.launch {
            if (content is TopicContent.Plain) {
                clipboardManager.setText(content.plain)
                toast.showMessage("success")
            } else {
                toast.showMessage("failed")
            }
        }
    }
    if (alreadyLoginIn) {
        val globalDialogController = LocalGlobalDialog.current
        ButtonNav(
            Icons.Default.PictureAsPdf,
            stringResource(Res.string.snapshot)
        ) {
            scope.launch {
                globalDialogController.use {
                    userSessionManager.getTopicSnapshot(topicInfo.id)
                    toast.showMessage(getString(Res.string.success))
                }
            }
        }
        ButtonNav(Icons.Default.Add, "Add") {
            dismissDialog()
            appNav.gotoTopicCompose(
                ObjectType.TOPIC,
                topicInfo.id,
                true,
                topicInfo.rootId.takeIf { topicInfo.rootType == ObjectType.ROOM && topicInfo.isEncrypted },
                null
            )
        }
    }

    val globalDialogController = LocalGlobalDialog.current
    ButtonNav(
        if (topicInfo.isPin) MaterialSymbolsOutlined.KeepOff else MaterialSymbolsOutlined.Keep,
        if (topicInfo.isPin) "Unpin" else "Pin"
    ) {
        scope.launch {
            pinOrUnpinTopic(topicInfo, userSessionManager, globalDialogController).onSuccess {
                dismissDialog()
            }
        }
    }
}

suspend fun pinOrUnpinTopic(
    topicInfo: TopicInfo,
    sessionManager: SessionManager,
    globalDialogController: GlobalDialogController,
): Result<TopicInfo> {
    return globalDialogController.use {
        if (topicInfo.isPin) {
            sessionManager.unpinTopic(topicInfo.id)
        } else {
            sessionManager.pinTopic(topicInfo.id)
        }.getOrThrow()
    }
}

@Composable
fun TopicDropdownMenu(expanded: Boolean, topicInfo: TopicInfo, onDismissRequest: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sessionManager = LocalSessionManager.current
    val globalDialogController = LocalGlobalDialog.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        val title = if (topicInfo.isPin) "Unpin" else "Pin"
        DropdownMenuItem(
            leadingIcon = {
                val char = when {
                    topicInfo.isPin -> MaterialSymbolsOutlined.KeepOff
                    else -> MaterialSymbolsOutlined.Keep
                }
                Box(modifier = Modifier.size(20.dp)) {
                    CustomIcon(IconRes.Font(char))
                }
            },
            text = { Text(title) },
            onClick = {
                scope.launch {
                    pinOrUnpinTopic(topicInfo, sessionManager, globalDialogController).onSuccess {
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
    hideSheet: () -> Unit,
) {
    BaseSheet(showSheet, sheetState, hideSheet) {
        SheetContainer {
            val preferenceData: StateFlow<String> by getPreference("gpt_model")
            val currentModel by preferenceData.collectAsState()
            val content = topicInfo.content
            Box(modifier = Modifier.height(200.dp).padding(horizontal = 20.dp).fillMaxWidth()) {
                if (content is TopicContent.Plain) {
                    val result by produceState<Result<Flow<GPTOutput>>?>(
                        null,
                        content,
                        currentModel
                    ) {
                        value = withContext(Dispatchers.IO) {
                            val (prompt, stopWord) = buildTranslatePrompt(
                                content.plain,
                                getCurrentLanguage(),
                                currentModel
                            )
                            Napier.i(tag = "gpt") {
                                "prompt $prompt"
                            }
                            buildGPT()
                                .generate(currentModel, prompt, stopWord)
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
    content: TopicContent.Plain,
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
            TopicContentField(
                topicInfo.copy(
                    content = content.copy(
                        plain = output
                    )
                )
            )
        }

        else ->
            result.exceptionOrNull()?.let {
                ExceptionView(
                    it,
                    modifier = Modifier.Companion.align(Alignment.Center)
                )
            }
    }
}
