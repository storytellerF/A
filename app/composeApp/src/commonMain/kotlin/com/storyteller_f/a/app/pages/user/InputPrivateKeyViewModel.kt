package com.storyteller_f.a.app.pages.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storyteller_f.shared.getAlgo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InputPrivateKeyViewModel : ViewModel() {
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
