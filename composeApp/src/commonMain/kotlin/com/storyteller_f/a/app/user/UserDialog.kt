package com.storyteller_f.a.app.user

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.logout
import a.composeapp.generated.resources.settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.compontents.ButtonNav
import com.storyteller_f.a.app.compontents.DialogContainer
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.shared.model.UserInfo
import org.jetbrains.compose.resources.stringResource


@Composable
fun UserDialogInternal(userInfo: UserInfo) {
    DialogContainer {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceDim, RoundedCornerShape(8.dp))
                .padding(8.dp), Arrangement.spacedBy(12.dp)
        ) {
            UserIcon(userInfo, size = 50.dp)
            Column {
                Text(userInfo.nickname)
                Text("aid: ${userInfo.aid}")
            }
        }

        Column {
            ButtonNav(Icons.Default.Settings, stringResource(Res.string.settings))
            ButtonNav(Icons.Default.Close, stringResource(Res.string.logout))
        }
    }
}


@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun UserDialog(
    userInfo: UserInfo?,
    showDialog: Boolean,
    dismiss: () -> Unit
) {
    if (userInfo != null && showDialog) {
        BasicAlertDialog({
            dismiss()
        }) {
            UserDialogInternal(userInfo)
        }
    }
}
