package com.storyteller_f.a.app.utils

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.decodeValueOrNull
import com.russhwolf.settings.serialization.encodeValue
import com.russhwolf.settings.serialization.removeValue
import com.storyteller_f.a.client_lib.ClientSession
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.shared.model.LoginUser
import kotlinx.serialization.ExperimentalSerializationApi

expect val defaultSettings: Settings

@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
fun restoreFromStorage() {
    val loginUser = defaultSettings.decodeValueOrNull(LoginUser.serializer(), "login_user") ?: return
    val privateKey = loginUser.privateKey
    val publicKey = loginUser.publicKey
    val address = loginUser.address
    LoginViewModel.updateState(ClientSession.SignUpSuccess(privateKey, publicKey, address))
}

@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
fun storeToStorage() {
    val state = LoginViewModel.state.value as ClientSession.SignUpSuccess
    val loginUser = LoginUser(state.privateKey, state.publicKey, state.address)
    defaultSettings.encodeValue(LoginUser.serializer(), "login_user", loginUser)
}

@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
fun clearStorage() {
    defaultSettings.removeValue<LoginUser>("login_user")
}
