package com.storyteller_f.a.app.compose_app.pages.topic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import com.storyteller_f.a.app.compose_app.LocalAppNavFactory
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalGlobalTask
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.LocalToaster
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.common.OnAddFavorite
import com.storyteller_f.a.app.compose_app.common.OnRemoveFavorite
import com.storyteller_f.a.app.compose_app.components.BaseSheet
import com.storyteller_f.a.app.compose_app.components.ButtonNav
import com.storyteller_f.a.app.compose_app.components.DialogContainer
import com.storyteller_f.a.app.compose_app.components.GlobalDialogController
import com.storyteller_f.a.app.compose_app.components.SheetContainer
import com.storyteller_f.a.app.compose_app.components.TopicContentField
import com.storyteller_f.a.app.compose_app.components.use
import com.storyteller_f.a.app.compose_app.copy
import com.storyteller_f.a.app.compose_app.pages.community.CommunityRefCell
import com.storyteller_f.a.app.compose_app.pages.room.RoomRefCell
import com.storyteller_f.a.app.compose_app.service.GPTOutput
import com.storyteller_f.a.app.compose_app.service.buildGPT
import com.storyteller_f.a.app.compose_app.service.buildTranslatePrompt
import com.storyteller_f.a.app.compose_app.snapshot
import com.storyteller_f.a.app.compose_app.success
import com.storyteller_f.a.app.compose_app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.app.compose_app.utils.setText
import com.storyteller_f.a.app.core.compontents.ExceptionView
import com.storyteller_f.a.app.core.compontents.IconRes
import com.storyteller_f.a.app.core.utils.getCurrentLanguage
import com.storyteller_f.a.client.core.LoadingState
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.addFavorite
import com.storyteller_f.a.client.core.getTopicSnapshot
import com.storyteller_f.a.client.core.pinTopic
import com.storyteller_f.a.client.core.removeFavorite
import com.storyteller_f.a.client.core.unpinTopic
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.NewFavorite
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
    val appNavFactory = LocalAppNavFactory.current
    DialogContainer {
        Text("pub: ${topicInfo.createdTime.formatTime()}")

        when (topicInfo.rootType) {
            ObjectType.COMMUNITY ->
                CommunityRefCell(topicInfo.rootId) {
                    dismissDialog()
                    appNavFactory.newAppNav().gotoCommunity(topicInfo.rootId, false)
                }

            ObjectType.ROOM ->
                RoomRefCell(topicInfo.rootId) {
                    dismissDialog()
                    appNavFactory.newAppNav().gotoRoom(topicInfo.rootId, false)
                }

            else -> {}
        }
        TopicDialogMenuList(topicInfo, dismissDialog)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicDialogMenuList(topicInfo: TopicInfo, dismissDialog: () -> Unit) {
    val content = topicInfo.content
    Column {
        CopyButton(content)
        SnapshotButton(topicInfo)
        TopicPinButton(topicInfo, dismissDialog)
        TranslateButton(content, topicInfo)
        FavoriteButton(topicInfo)
    }
}

@Composable
private fun FavoriteButton(topicInfo: TopicInfo) {
    val sessionManager = LocalSessionManager.current
    val dialogController = LocalGlobalTask.current
    val scope = rememberCoroutineScope()
    val favoriteId = topicInfo.favoriteId
    val state = dialogController.stateMap["favorite-${topicInfo.id}"]
    val icon = if (state is LoadingState.Loading) {
        IconRes.Loading
    } else if (favoriteId != null) {
        IconRes.Vector(Icons.Default.Favorite)
    } else {
        IconRes.Vector(Icons.Default.FavoriteBorder)
    }
    ButtonNav(icon, "Favorite") {
        scope.launch {
            dialogController.use("favorite-${topicInfo.id}") { state, bus ->
                state.use {
                    if (favoriteId != null) {
                        sessionManager.removeFavorite(favoriteId).onSuccess {
                            bus.emit(OnRemoveFavorite(topicInfo.tuple()))
                        }
                    } else {
                        sessionManager.addFavorite(NewFavorite(ObjectType.TOPIC, topicInfo.id)).onSuccess {
                            bus.emit(OnAddFavorite(it))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyButton(content: TopicContent) {
    val toast = LocalToaster.current
    val clipboardManager = LocalClipboard.current
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
}

@Composable
private fun SnapshotButton(topicInfo: TopicInfo) {
    val toast = LocalToaster.current
    val scope = rememberCoroutineScope()
    val userSessionManager = LocalSessionManager.current
    val alreadyLoginIn by userSessionManager.isAlreadySignIn.collectAsState()
    if (!alreadyLoginIn) return
    val globalDialogController = LocalGlobalDialog.current
    ButtonNav(
        Icons.Default.PictureAsPdf,
        stringResource(Res.string.snapshot)
    ) {
        scope.launch {
            globalDialogController.useResult {
                userSessionManager.getTopicSnapshot(topicInfo.id)
            }.onSuccess {
                toast.showMessage(getString(Res.string.success))
            }
        }
    }
}

@Composable
private fun TopicPinButton(topicInfo: TopicInfo, dismissDialog: () -> Unit) {
    val scope = rememberCoroutineScope()
    val userSessionManager = LocalSessionManager.current
    val globalDialogController = LocalGlobalDialog.current
    ButtonNav(
        if (topicInfo.isPin) MaterialSymbolsOutlined.KeepOff else MaterialSymbolsOutlined.Keep,
        if (topicInfo.isPin) "Unpin" else "Pin"
    ) {
        scope.launch {
            globalDialogController.pinOrUnpinTopic(topicInfo, userSessionManager).onSuccess {
                dismissDialog()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TranslateButton(content: TopicContent, topicInfo: TopicInfo) {
    val toasterState = LocalToaster.current
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

suspend fun GlobalDialogController.pinOrUnpinTopic(
    topicInfo: TopicInfo,
    sessionManager: UserSessionManager,
): Result<TopicInfo> {
    return useResult {
        if (topicInfo.isPin) {
            sessionManager.unpinTopic(topicInfo.id)
        } else {
            sessionManager.pinTopic(topicInfo.id)
        }
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
