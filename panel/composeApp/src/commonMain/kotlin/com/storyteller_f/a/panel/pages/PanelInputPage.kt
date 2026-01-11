package com.storyteller_f.a.panel.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storyteller_f.a.app.core.CoreStrings
import com.storyteller_f.a.app.core.components.CenterBox
import com.storyteller_f.a.app.core.components.PrivateKeyInput
import com.storyteller_f.a.app.core.components.request
import com.storyteller_f.a.client.core.getPanelUserPass
import com.storyteller_f.a.panel.LocalPanelGlobalDialog
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.AlgoType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PanelInputViewModel : ViewModel() {
    val privateKey = MutableStateFlow("")

    val publicKey = privateKey.map {
        getAlgo().run {
            getDerPublicKeyFromPrivateKey(it).getOrNull()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val address = publicKey.map {
        getAlgo().run {
            it?.let { derPublicKeyStr -> calcAddress(derPublicKeyStr).getOrNull() }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun updatePrivateKey(privateKey: String) {
        this.privateKey.value = privateKey
    }

    fun autoGeneratePrivateKey() {
        viewModelScope.launch {
            getAlgo().generatePemKeyPair().onSuccess { (privateKey, _) ->
                updatePrivateKey(privateKey)
            }
        }
    }
}

@Composable
fun PanelInputPage(back: () -> Unit) {
    val viewModel: PanelInputViewModel = viewModel()
    val dialogController = LocalPanelGlobalDialog.current
    CenterBox {
        val scope = rememberCoroutineScope()
        val privateKey by viewModel.privateKey.collectAsState()
        val address by viewModel.address.collectAsState()

        val startSign: () -> Unit = {
            scope.launch {
                dialogController.useResult {
                    request {
                        runCatching {
                            getPanelUserPass(
                                privateKey,
                                AlgoType.P256,
                                false
                            ) {
                                historyFactory.addSession(it)
                            }
                        }
                    }
                }.onSuccess {
                    back()
                }
            }
        }
        Column(modifier = Modifier.padding(20.dp)) {
            PrivateKeyInput(
                privateKey = privateKey,
                address = address,
                enableRandom = false,
                update = {
                    viewModel.updatePrivateKey(it)
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(startSign) {
                    Text(CoreStrings.start())
                }
            }
        }
    }
}
