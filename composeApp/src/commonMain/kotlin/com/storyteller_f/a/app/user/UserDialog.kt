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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.serialization.removeValue
import com.storyteller_f.a.app.compontents.*
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.shared.model.LoginUser
import com.storyteller_f.shared.model.UserInfo
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
@Composable
fun UserDialogInternal(userInfo: UserInfo) {
    var alertDialogState by remember {
        mutableStateOf<AlertDialogState?>(null)
    }
    val my by LoginViewModel.user.collectAsState()
    DialogContainer {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceDim, RoundedCornerShape(8.dp))
                .padding(8.dp),
            Arrangement.spacedBy(12.dp)
        ) {
            UserIcon(userInfo, size = 50.dp)
            Column {
                Text(userInfo.nickname)
                Text("aid: ${userInfo.aid}")
            }
        }

        Column {
            if (my?.id == userInfo.id) {
                ButtonNav(Icons.Default.Settings, stringResource(Res.string.settings))
                ButtonNav(Icons.Default.Close, stringResource(Res.string.logout)) {
                    alertDialogState = AlertDialogState(null, "Are you sure to logout?")
                }
            }
        }
    }
    CustomAlertDialog(alertDialogState, {
        alertDialogState = null
    }) {
        LoginViewModel.logout()
        com.storyteller_f.a.app.settings.removeValue<LoginUser>("login_user")
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
