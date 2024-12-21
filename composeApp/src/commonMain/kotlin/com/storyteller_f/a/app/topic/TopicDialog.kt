package com.storyteller_f.a.app.topic

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.buildAnnotatedString
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.compontents.ButtonNav
import com.storyteller_f.a.app.compontents.DialogContainer
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.model.createUserViewModel
import com.storyteller_f.a.app.user.UserCell
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.getTopicSnapshot
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
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

            TopicDialogInternal(topicInfo, authorInfo)
        }
    }
}

@Composable
fun TopicDialogInternal(topicInfo: TopicInfo, authorInfo: UserInfo?) {
    val clipboardManager = LocalClipboardManager.current
    val appNav = LocalAppNav.current
    val alreadyLoginIn by LoginViewModel.isAlreadySignUp.collectAsState(false)
    val toasterState = rememberToasterState()
    Toaster(toasterState)
    DialogContainer {
        UserCell(authorInfo, true)
        Text("pub: ${topicInfo.lastModifiedTime}")
        Column {
            val content = topicInfo.content
            if (content is TopicContent.Plain) {
                ButtonNav(Icons.Default.ContentCopy, stringResource(Res.string.copy)) {
                    clipboardManager.setText(annotatedString = buildAnnotatedString {
                        append(content.plain)
                    })
                }
                val scope = rememberCoroutineScope()
                val successText = stringResource(Res.string.success)
                if (alreadyLoginIn) {
                    ButtonNav(Icons.Default.PictureAsPdf, stringResource(Res.string.snapshot)) {
                        scope.launch {
                            globalDialogState.use {
                                client.getTopicSnapshot(topicInfo.id)
                                toasterState.show(successText, duration = 1.seconds)
                            }
                        }
                    }
                    ButtonNav(Icons.Default.Add, "Add") {
                        appNav.gotoTopicCompose(
                            ObjectType.TOPIC,
                            topicInfo.id,
                            true,
                            topicInfo.rootId.takeIf { topicInfo.rootType == ObjectType.ROOM && topicInfo.isPrivate }
                        )
                    }
                }
            }
        }
    }
}
