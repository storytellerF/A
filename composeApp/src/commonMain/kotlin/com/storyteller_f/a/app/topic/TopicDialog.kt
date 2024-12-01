package com.storyteller_f.a.app.topic

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.compontents.DialogContainer
import com.storyteller_f.a.app.user.UserCell
import com.storyteller_f.a.app.user.UserViewModel
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicDialog(topicInfo: TopicInfo?, showDialog: Boolean, dismiss: () -> Unit) {
    if (topicInfo != null && showDialog) {
        BasicAlertDialog({
            dismiss()
        }) {
            val author = topicInfo.author
            val authorViewModel = viewModel(UserViewModel::class, keys = listOf("user", author)) {
                UserViewModel(author)
            }
            val authorInfo by authorViewModel.handler.data.collectAsState()

            TopicDialogInternal(topicInfo, authorInfo)
        }
    }
}

@Composable
fun TopicDialogInternal(topicInfo: TopicInfo, authorInfo: UserInfo?) {
    DialogContainer {
        UserCell(authorInfo, true)
        Text("pub: ${topicInfo.lastModifiedTime}")
        Column {
//            ButtonNav(Icons.Default.Add, "Snapshot") {
//            }
        }
    }
}
