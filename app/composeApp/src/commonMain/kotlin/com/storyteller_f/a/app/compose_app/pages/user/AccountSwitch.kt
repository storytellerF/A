package com.storyteller_f.a.app.compose_app.pages.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.compose_app.CustomSessionManager
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalMainSessionManager
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.common.StateView
import com.storyteller_f.a.app.compose_app.compontents.BaseSheet
import com.storyteller_f.a.app.compose_app.compontents.ButtonNav
import com.storyteller_f.a.app.compose_app.compontents.GlobalDialogController
import com.storyteller_f.a.app.compose_app.compontents.SheetContainer
import com.storyteller_f.a.app.compose_app.model.getAlternativeAccountsViewModel
import com.storyteller_f.a.app.compose_app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.client.core.RawUserPass
import com.storyteller_f.a.client.core.RawUserPassInfo
import com.storyteller_f.a.client.core.addAlternativeAccount
import com.storyteller_f.shared.calcAddress
import com.storyteller_f.shared.getDerPublicKeyFromPrivateKey
import com.storyteller_f.shared.getPemPrivateKeyFromDer
import com.storyteller_f.shared.utils.mapResult
import kotlinx.coroutines.launch

class AccountSwitcher(val state: MutableState<Boolean> = mutableStateOf(false)) {
    fun switch() {
        state.value = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitch(accountSwitcher: AccountSwitcher, switchToMain: () -> Unit, switch: (String) -> Unit) {
    var expand by accountSwitcher.state
    val sheetState = rememberModalBottomSheetState()
    BaseSheet(expand, sheetState, {
        expand = false
    }) {
        SheetContainer {
            val (mainSessionManager, isSwitched) = isSwitched()
            val scope = rememberCoroutineScope()
            val globalDialogController = LocalGlobalDialog.current
            CompositionLocalProvider(LocalSessionManager provides mainSessionManager) {
                val viewModel = getAlternativeAccountsViewModel()
                if (isSwitched) {
                    ButtonNav(MaterialSymbolsOutlined.ArrowBack, "Back to main account") {
                        switchToMain()
                    }
                } else {
                    val pagingItems = viewModel.flow.collectAsLazyPagingItems()
                    ButtonNav(Icons.Default.Add, "Add alternative Account") {
                        scope.launch {
                            globalDialogController.useResult {
                                mainSessionManager.addAlternativeAccount()
                            }.getOrNull()?.let {
                                pagingItems.refresh()
                            }
                        }
                    }
                }
                StateView(viewModel, modifier = Modifier.height(300.dp)) { pagingItems ->
                    LazyColumn(
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            pagingItems.itemSnapshotList.size,
                            key = pagingItems.itemKey { accountInfo ->
                                accountInfo.id
                            }
                        ) { index ->
                            val alternativeAccountInfo = pagingItems[index]
                            alternativeAccountInfo?.let {
                                UserCell(it.userInfo, false) {
                                    switch(alternativeAccountInfo.privateKey)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun isSwitched(): Pair<CustomSessionManager, Boolean> {
    val currentUserSessionManager = LocalSessionManager.current
    val mainSessionManager = LocalMainSessionManager.current
    val currentAddress by currentUserSessionManager.address.collectAsState()
    val mainAddress by mainSessionManager.address.collectAsState()
    val isSwitched = currentAddress != mainAddress
    return Pair(mainSessionManager, isSwitched)
}

suspend fun switchUser(
    globalDialogController: GlobalDialogController,
    derPrivateKeyStr: String,
    switch: (RawUserPass) -> Unit,
) {
    globalDialogController.useResult {
        getPemPrivateKeyFromDer(derPrivateKeyStr).mapResult { pemPrivateKey ->
            getDerPublicKeyFromPrivateKey(pemPrivateKey).mapResult { publicKey ->
                calcAddress(publicKey).map { address ->
                    RawUserPass(RawUserPassInfo(pemPrivateKey, publicKey, address))
                }
            }
        }
    }.getOrNull()?.let {
        switch(it)
    }
}
