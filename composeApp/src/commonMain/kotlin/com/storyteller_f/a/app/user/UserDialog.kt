package com.storyteller_f.a.app.user

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.settings
import a.composeapp.generated.resources.sign_out
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.serialization.removeValue
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.compontents.*
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.signOut
import com.storyteller_f.shared.model.LoginUser
import com.storyteller_f.shared.model.UserInfo
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
@Composable
fun UserDialogInternal(userInfo: UserInfo) {
    val controller = remember {
        CustomAlertDialogController()
    }
    val my by LoginViewModel.user.collectAsState()
    DialogContainer {
        UserCell(userInfo, false, avatarSize = 50.dp)

        Column {
            if (my?.id == userInfo.id) {
                ButtonNav(Icons.Default.Settings, stringResource(Res.string.settings))
                ButtonNav(Icons.AutoMirrored.Default.Logout, stringResource(Res.string.sign_out)) {
                    controller.showMessage("Are you sure to sign out?", "")
                }
            }
        }
    }
    val scope = rememberCoroutineScope()
    CustomAlertDialog(controller, {
        controller.close()
    }) {
        scope.launch {
            globalDialogState.use {
                client.signOut()
                LoginViewModel.signOut()
                com.storyteller_f.a.app.settings.removeValue<LoginUser>("login_user")
            }
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
