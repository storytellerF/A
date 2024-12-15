package com.storyteller_f.a.app.topic

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.copy
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.buildAnnotatedString
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.compontents.ButtonNav
import com.storyteller_f.a.app.compontents.DialogContainer
import com.storyteller_f.a.app.user.UserCell
import com.storyteller_f.a.app.user.UserViewModel
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicDialog(topicInfo: TopicInfo?, showDialog: Boolean, dismiss: () -> Unit) {
    if (topicInfo != null && showDialog) {
        BasicAlertDialog({
            dismiss()
        }) {
            val author = topicInfo.author
            val authorViewModel = viewModel(keys = listOf("user", author)) {
                UserViewModel(author)
            }
            val authorInfo by authorViewModel.handler.data.collectAsState()

            TopicDialogInternal(topicInfo, authorInfo)
        }
    }
}

@Composable
fun TopicDialogInternal(topicInfo: TopicInfo, authorInfo: UserInfo?) {
    val clipboardManager = LocalClipboardManager.current
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
            }
        }
    }
}
