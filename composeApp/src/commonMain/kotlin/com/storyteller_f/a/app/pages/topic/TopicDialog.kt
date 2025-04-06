package com.storyteller_f.a.app.pages.topic

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.copy
import a.composeapp.generated.resources.snapshot
import a.composeapp.generated.resources.success
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import com.dokar.sonner.ToasterState
import com.storyteller_f.a.app.*
import com.storyteller_f.a.app.compontents.ButtonNav
import com.storyteller_f.a.app.compontents.DialogContainer
import com.storyteller_f.a.app.model.createUserViewModel
import com.storyteller_f.a.app.pages.community.CommunityRefCell
import com.storyteller_f.a.app.pages.room.RoomRefCell
import com.storyteller_f.a.app.pages.user.UserCell
import com.storyteller_f.a.app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.app.utils.setText
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.getTopicSnapshot
import com.storyteller_f.a.client_lib.pinTopic
import com.storyteller_f.a.client_lib.unpinTopic
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.formatTime
import kotlinx.coroutines.launch
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
fun TopicDialogInternal(topicInfo: TopicInfo, authorInfo: UserInfo?, dismiss: () -> Unit) {
    val clipboardManager = LocalClipboard.current
    val appNav = LocalAppNav.current
    val alreadyLoginIn by LoginViewModel.isAlreadySignUp.collectAsState(false)
    val toasterState = LocalToaster.current
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
        TopicDialogMenuList(topicInfo, clipboardManager, alreadyLoginIn, toasterState, dismiss, appNav)
    }
}

@Composable
private fun TopicDialogMenuList(
    topicInfo: TopicInfo,
    clipboardManager: Clipboard,
    alreadyLoginIn: Boolean,
    toasterState: ToasterState,
    dismiss: () -> Unit,
    appNav: AppNav
) {
    val scope = rememberCoroutineScope()
    Column {
        val content = topicInfo.content
        if (content is TopicContent.Plain) {
            ButtonNav(Icons.Default.ContentCopy, stringResource(Res.string.copy)) {
                scope.launch {
                    clipboardManager.setText(content.plain)
                }
            }
            val successText = stringResource(Res.string.success)
            if (alreadyLoginIn) {
                val client = LocalClient.current
                ButtonNav(Icons.Default.PictureAsPdf, stringResource(Res.string.snapshot)) {
                    scope.launch {
                        globalDialogState.use {
                            client.getTopicSnapshot(topicInfo.id)
                            toasterState.show(successText, duration = 1.seconds)
                        }
                    }
                }
                ButtonNav(Icons.Default.Add, "Add") {
                    dismiss()
                    appNav.gotoTopicCompose(
                        ObjectType.TOPIC,
                        topicInfo.id,
                        true,
                        topicInfo.rootId.takeIf { topicInfo.rootType == ObjectType.ROOM && topicInfo.isPrivate }
                    )
                }
            }

            val client = LocalClient.current

            ButtonNav(
                if (topicInfo.isPin) MaterialSymbolsOutlined.KeepOff else MaterialSymbolsOutlined.Keep,
                if (topicInfo.isPin) "Unpin" else "Pin"
            ) {
                scope.launch {
                    globalDialogState.use {
                        if (topicInfo.isPin) {
                            client.unpinTopic(topicInfo.id)
                        } else {
                            client.pinTopic(topicInfo.id)
                        }
                    }
                }
            }
        }
    }
}

