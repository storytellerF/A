package com.storyteller_f.a.panel.pages

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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.storyteller_f.a.api.NewUser
import com.storyteller_f.a.app.core.CoreStrings
import com.storyteller_f.a.app.core.components.DialogContainer
import com.storyteller_f.a.app.core.components.GlobalDialogContext
import com.storyteller_f.a.app.core.components.GlobalDialogController
import com.storyteller_f.a.app.core.components.LocalToaster
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.Toast
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.app.core.components.safeArea
import com.storyteller_f.a.app.core.components.setText
import com.storyteller_f.a.client.core.addUser
import com.storyteller_f.a.panel.CustomPanelSessionManager
import com.storyteller_f.a.panel.LocalPanelGlobalDialog
import com.storyteller_f.a.panel.LocalPanelNav
import com.storyteller_f.a.panel.Res
import com.storyteller_f.a.panel.add
import com.storyteller_f.a.panel.add_user
import com.storyteller_f.a.panel.aid
import com.storyteller_f.a.panel.all_users
import com.storyteller_f.a.panel.common.AddUserViewModel
import com.storyteller_f.a.panel.common.AllUsersViewModel
import com.storyteller_f.a.panel.common.OnUserAdded
import com.storyteller_f.a.panel.common.createPanelAllUserViewModel
import com.storyteller_f.a.panel.components.UserCell
import com.storyteller_f.a.panel.edit_private_key
import com.storyteller_f.a.panel.menu
import com.storyteller_f.a.panel.nickname
import com.storyteller_f.a.panel.private_key_required
import com.storyteller_f.a.panel.random
import com.storyteller_f.shared.replaceCrlf
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun AllUsersPage() {
    val viewModel = createPanelAllUserViewModel()
    AllUsersPageInternal(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllUsersPageInternal(viewModel: AllUsersViewModel) {
    val panelNav = LocalPanelNav.current
    Scaffold(
        topBar = {
            var showDialog by remember {
                mutableStateOf(false)
            }
            TopAppBar(title = {
                Text(stringResource(Res.string.all_users))
            }, navigationIcon = {
                IconButton({ panelNav.open() }) { Icon(Icons.Default.Menu, stringResource(Res.string.menu)) }
            }, actions = {
                IconButton({
                    showDialog = true
                }) {
                    Icon(Icons.Default.Add, stringResource(Res.string.add_user))
                }
            })
            AddUserDialog(showDialog) {
                showDialog = false
            }
        }
    ) {
        val direction = LocalLayoutDirection.current
        Box(Modifier.safeArea(it, direction)) {
            StateView(viewModel) { items ->
                LazyColumn {
                    pagingItems(items, key = {
                        it.id
                    }) {
                        val info = items.get(it)
                        UserCell(info) {
                            val uid = info?.id
                            if (uid != null) {
                                panelNav.gotoUserDetail(uid)
                            }
                        }
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
                Icon(Icons.Default.FileOpen, CoreStrings.selectFile())
            }
            IconButton({
                addUserViewModel.autoGeneratePrivateKey()
            }) {
                Icon(Icons.Default.Casino, stringResource(Res.string.random))
            }
            Spacer(modifier = Modifier.weight(1f))
        }
        OutlinedTextField(privateKey, {
            addUserViewModel.updatePrivateKey(it)
        }, label = {
            Text(CoreStrings.privateKey())
        })
        Button({
            addUserNavigator.popBackStack()
        }) {
            Text(CoreStrings.ok())
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
            Text(stringResource(Res.string.nickname))
        })
        OutlinedTextField(aid, {
            addUserViewModel.updateAid(it)
        }, label = {
            Text(stringResource(Res.string.aid))
        })
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        IconButton(onClick = {
            scope.launch {
                clipboard.setText(addUserViewModel.privateKey.value)
            }
        }) {
            Icon(Icons.Default.ContentCopy, "copy")
        }
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
                Icon(Icons.Default.Edit, stringResource(Res.string.edit_private_key))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button({
                dismiss()
            }) {
                Text(CoreStrings.cancel())
            }
            val scope = rememberCoroutineScope()
            val globalDialogController = LocalPanelGlobalDialog.current
            val toast = LocalToaster.current
            val requiredPrivateKeyMessage = stringResource(Res.string.private_key_required)
            Button({
                globalDialogController.addUser(scope, addUserViewModel, toast, dismiss, requiredPrivateKeyMessage)
            }) {
                Text(stringResource(Res.string.add))
            }
        }
    }
}

private fun GlobalDialogController<GlobalDialogContext<CustomPanelSessionManager>>.addUser(
    scope: CoroutineScope,
    addUserViewModel: AddUserViewModel,
    toast: Toast,
    dismiss: () -> Unit,
    requiredPrivateKeyMessage: String
) {
    val nickname = addUserViewModel.nickname.value.takeIf { it.isNotBlank() }
    val aid = addUserViewModel.aid.value.takeIf { it.isNotBlank() }
    val publicKey = addUserViewModel.publicKey.value?.takeIf { it.isNotBlank() }
    if (publicKey == null) {
        toast.showMessage(requiredPrivateKeyMessage)
        return
    }
    val newUser = NewUser(nickname, aid, publicKey)
    scope.launch {
        useResult {
            context.request { addUser(newUser) }.onSuccess {
                context.emitEvent(OnUserAdded(it))
            }
        }.onSuccess {
            dismiss()
        }
    }
}
