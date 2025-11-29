package com.storyteller_f.a.app.pages.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.AppGlobalDialogController
import com.storyteller_f.a.app.LocalAccountSwitcher
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.LocalUiViewModel
import com.storyteller_f.a.app.UIViewModel
import com.storyteller_f.a.app.common.ChildAccountsViewModel
import com.storyteller_f.a.app.common.getChildAccountsViewModel
import com.storyteller_f.a.app.core.CoreStrings
import com.storyteller_f.a.app.core.components.BaseSheet
import com.storyteller_f.a.app.core.components.CustomIcon
import com.storyteller_f.a.app.core.components.SheetContainer
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.UserIcon
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.client.core.RawUserPass
import com.storyteller_f.a.client.core.RawUserPassInfo
import com.storyteller_f.a.client.core.addChildAccount
import com.storyteller_f.shared.algoRunCatching
import com.storyteller_f.shared.model.UserInfo
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

class AccountSwitcher(val state: MutableState<Boolean> = mutableStateOf(false)) {
    fun switch() {
        state.value = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitch() {
    val accountSwitcher = LocalAccountSwitcher.current
    var expand by accountSwitcher.state
    val sheetState = rememberModalBottomSheetState()
    BaseSheet(expand, sheetState, {
        expand = false
    }) {
        SheetContainer {
            val viewModel = getChildAccountsViewModel()
            AccountSwitchInternal(viewModel)
        }
    }
}

@Composable
private fun AccountSwitchInternal(viewModel: ChildAccountsViewModel) {
    val isInChildAccount by isInChildAccount()
    val uiViewModel = LocalUiViewModel.current
    val mainSessionManager = uiViewModel.mainInstance.sessionManager
    val globalDialogController = LocalGlobalDialog.current
    val scope = rememberCoroutineScope()
    Row(modifier = Modifier.padding(horizontal = 10.dp)) {
        if (isInChildAccount) {
            FilledIconButton({
                val rawUserPass = mainSessionManager.model.currentUserPass as? RawUserPass
                uiViewModel.childAccount.value = rawUserPass
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Switch to main account")
            }
        }
        val pagingItems = viewModel.flow.collectAsLazyPagingItems()
        FilledIconButton({
            scope.launch {
                globalDialogController.useResult {
                    mainSessionManager.addChildAccount()
                }.getOrNull()?.let {
                    pagingItems.refresh()
                }
            }
        }) {
            Icon(Icons.Default.Add, "add")
        }
    }
    StateView(viewModel, modifier = Modifier.height(300.dp)) { pagingItems ->
        LazyColumn(
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            pagingItems(
                pagingItems,
                key = { accountInfo ->
                    accountInfo.id
                }
            ) { index ->
                val childAccountInfo = pagingItems[index]
                childAccountInfo?.let {
                    ChildAccountCell(it.userInfo) {
                        scope.launch {
                            globalDialogController.switchUser(childAccountInfo.privateKey, uiViewModel)
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun ChildAccountCell(userInfo: UserInfo?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.padding(8.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UserIcon(
            setClickEvent = false,
            avatarUrl = userInfo?.avatar?.url,
        ) {}
        if (userInfo != null) {
            Column {
                Text(userInfo.nickname, style = MaterialTheme.typography.titleMedium)
                val aid = userInfo.aid
                if (aid != null) {
                    Text(CoreStrings.aid(aid), style = MaterialTheme.typography.labelSmall)
                } else {
                    Text(CoreStrings.ad(userInfo.address), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        CustomIcon(Icons.AutoMirrored.Filled.Input)
    }
}

@Composable
fun isInChildAccount(): State<Boolean> {
    val inspectionMode = LocalInspectionMode.current
    if (inspectionMode) {
        return remember {
            mutableStateOf(false)
        }
    }
    val uiViewModel = LocalUiViewModel.current
    val childAccount by uiViewModel.childAccount.collectAsState()
    return remember {
        derivedStateOf {
            childAccount != null
        }
    }
}

suspend fun AppGlobalDialogController.switchUser(
    derPrivateKeyStr: String,
    uiViewModel: UIViewModel
) {
    useResult {
        algoRunCatching {
            val pemPrivateKey = getPemPrivateKeyFromDer(derPrivateKeyStr).getOrThrow()
            val publicKey = getDerPublicKeyFromPrivateKey(pemPrivateKey).getOrThrow()
            val address = calcAddress(publicKey).getOrThrow()
            RawUserPass(RawUserPassInfo(pemPrivateKey, publicKey, address))
        }
    }.getOrNull()?.let {
        uiViewModel.childAccount.value = it
    }
}
