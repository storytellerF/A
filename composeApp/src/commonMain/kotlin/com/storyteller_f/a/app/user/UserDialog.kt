package com.storyteller_f.a.app.user

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.logout
import a.composeapp.generated.resources.settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    val controller = remember {
        CustomAlertDialogController()
    }
    val my by LoginViewModel.user.collectAsState()
    DialogContainer {
        UserCell(userInfo, false, avatarSize = 50.dp)

        Column {
            if (my?.id == userInfo.id) {
                ButtonNav(Icons.Default.Settings, stringResource(Res.string.settings))
                ButtonNav(Icons.AutoMirrored.Default.Logout, stringResource(Res.string.logout)) {
                    controller.showMessage("Are you sure to logout?", "")
                }
            }
        }
    }
    CustomAlertDialog(controller, {
        controller.close()
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
