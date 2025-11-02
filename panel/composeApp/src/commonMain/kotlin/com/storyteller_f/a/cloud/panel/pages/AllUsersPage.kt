package com.storyteller_f.a.cloud.panel.pages

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.storyteller_f.a.api.core.NewUser
import com.storyteller_f.a.app.core.compontents.DialogContainer
import com.storyteller_f.a.app.core.compontents.GlobalDialogController
import com.storyteller_f.a.app.core.compontents.LocalGlobalDialog
import com.storyteller_f.a.app.core.compontents.LocalToaster
import com.storyteller_f.a.app.core.compontents.StateView
import com.storyteller_f.a.app.core.compontents.Toast
import com.storyteller_f.a.app.core.compontents.pagingItems
import com.storyteller_f.a.client.core.addUser
import com.storyteller_f.a.cloud.panel.CustomPanelSessionManager
import com.storyteller_f.a.cloud.panel.LocalSessionManager
import com.storyteller_f.a.cloud.panel.common.AddUserViewModel
import com.storyteller_f.a.cloud.panel.common.AllUsersViewModel
import com.storyteller_f.a.cloud.panel.common.OnUserAdded
import com.storyteller_f.a.cloud.panel.common.createPanelAllUserViewModel
import com.storyteller_f.a.cloud.panel.components.UserCell
import com.storyteller_f.shared.replaceCrlf
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AllUsersPage() {
    val viewModel = createPanelAllUserViewModel()
    AllUsersPageInternal(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllUsersPageInternal(viewModel: AllUsersViewModel) {
    Scaffold(
        topBar = {
            var showDialog by remember {
                mutableStateOf(false)
            }
            TopAppBar(title = {
                Text("All users")
            }, actions = {
                IconButton({
                    showDialog = true
                }) {
                    Icon(Icons.Default.Add, "add user")
                }
            })
            AddUserDialog(showDialog) {
                showDialog = false
            }
        }
    ) {
        Box(Modifier.padding(top = it.calculateTopPadding())) {
            StateView(viewModel) { items ->
                LazyColumn {
                    pagingItems(items, key = {
                        it.id
                    }) {
                        UserCell(items.get(it))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUserDialog(
    showDialog: Boolean,
    dismiss: () -> Unit,
) {
    if (showDialog) {
        BasicAlertDialog(
            {
                dismiss()
            },
        ) {
            AddUserInternal(dismiss)
        }
    }
}

@Composable
fun AddUserInternal(dismiss: () -> Unit) {
    val addUserViewModel = viewModel {
        AddUserViewModel()
    }
    DialogContainer {
        val addUserNavigator = rememberNavController()
        NavHost(addUserNavigator, "start") {
            composable("start") {
                AddUserProfilePage(addUserViewModel, addUserNavigator, dismiss)
            }
            composable("private_key") {
                AddUserPrivateKeyPage(addUserViewModel, addUserNavigator)
            }
        }
    }
}

@Composable
private fun AddUserPrivateKeyPage(
    addUserViewModel: AddUserViewModel,
    addUserNavigator: NavHostController
) {
    val privateKey by addUserViewModel.privateKey.collectAsState()
    val scope = rememberCoroutineScope()
    Column {
        Row {
            IconButton({
                scope.launch {
                    val f = FileKit.openFilePicker()
                    if (f != null) {
                        val privateKey = String(f.readBytes()).replaceCrlf()
                        addUserViewModel.updatePrivateKey(privateKey)
                    }
                }
            }) {
                Icon(Icons.Default.FileOpen, "select file")
            }
            IconButton({
                addUserViewModel.autoGeneratePrivateKey()
            }) {
                Icon(Icons.Default.Casino, "random")
            }
            Spacer(modifier = Modifier.weight(1f))
        }
        OutlinedTextField(privateKey, {
            addUserViewModel.updatePrivateKey(it)
        }, label = {
            Text("private key")
        })
        Button({
            addUserNavigator.popBackStack()
        }) {
            Text("OK")
        }
    }
}

@Composable
private fun AddUserProfilePage(
    addUserViewModel: AddUserViewModel,
    addUserNavigator: NavHostController,
    dismiss: () -> Unit
) {
    val nickname by addUserViewModel.nickname.collectAsState()
    val aid by addUserViewModel.aid.collectAsState()
    val address by addUserViewModel.address.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(nickname, {
            addUserViewModel.updateNickname(it)
        }, label = {
            Text("Nickname")
        })
        OutlinedTextField(aid, {
            addUserViewModel.updateAid(it)
        }, label = {
            Text("Aid")
        })
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shape = RoundedCornerShape(10.dp)
            Text(
                address ?: "",
                modifier = Modifier.weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.primaryContainer, shape)
                    .padding(8.dp)
            )
            IconButton({
                addUserNavigator.navigate("private_key")
            }) {
                Icon(Icons.Default.Edit, "edit private key")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button({
                dismiss()
            }) {
                Text("Cancel")
            }
            val scope = rememberCoroutineScope()
            val globalDialogController = LocalGlobalDialog.current
            val sessionManager = LocalSessionManager.current
            val toast = LocalToaster.current
            Button({
                globalDialogController.addUser(
                    scope,
                    sessionManager,
                    addUserViewModel,
                    toast,
                    dismiss
                )
            }) {
                Text("Add")
            }
        }
    }
}

private fun GlobalDialogController.addUser(
    scope: CoroutineScope,
    sessionManager: CustomPanelSessionManager,
    addUserViewModel: AddUserViewModel,
    toast: Toast,
    dismiss: () -> Unit
) {
    val nickname = addUserViewModel.nickname.value.takeIf { it.isNotBlank() }
    val aid = addUserViewModel.aid.value.takeIf { it.isNotBlank() }
    val publicKey = addUserViewModel.publicKey.value?.takeIf { it.isNotBlank() }
    if (publicKey == null) {
        toast.showMessage("Private key must be specified")
        return
    }
    val newUser = NewUser(nickname, aid, publicKey)
    scope.launch {
        useResult {
            sessionManager.addUser(newUser).onSuccess {
                emitEvent(OnUserAdded(it))
            }
        }.onSuccess {
            dismiss()
        }
    }
}
