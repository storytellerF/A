package com.storyteller_f.a.app

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.auto_generate
import a.composeapp.generated.resources.go_to_sign_in
import a.composeapp.generated.resources.go_to_sign_up
import a.composeapp.generated.resources.input_private_key
import a.composeapp.generated.resources.private_key
import a.composeapp.generated.resources.sign_in
import a.composeapp.generated.resources.sign_up
import a.composeapp.generated.resources.start_sign_in
import a.composeapp.generated.resources.start_sign_up
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.serialization.decodeValueOrNull
import com.russhwolf.settings.serialization.encodeValue
import com.storyteller_f.a.app.common.CenterBox
import com.storyteller_f.a.app.compontents.MeasureTextLineCount
import com.storyteller_f.a.client_lib.ClientSession
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.getData
import com.storyteller_f.a.client_lib.signIn
import com.storyteller_f.a.client_lib.signUp
import com.storyteller_f.shared.calcAddress
import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.generateKeyPair
import com.storyteller_f.shared.getDerPublicKeyFromPrivateKey
import com.storyteller_f.shared.model.LoginUser
import com.storyteller_f.shared.signature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max

@Composable
fun LoginPage() {
    val navigator = rememberNavController()
    val nav = remember {
        object : LoginNav {

            override fun gotoPrivateKey() {
                if (!navigator.popBackStack("/input_private_key", false)) {
                    navigator.navigate("/input_private_key")
                }
            }

            override fun gotoSignUp() {
                if (!navigator.popBackStack("/select_signup", false)) {
                    navigator.navigate("/select_signup")
                }
            }

            override fun gotoLogin() {
                if (!navigator.popBackStack("/select_login", false)) {
                    navigator.navigate("/select_login")
                }
            }
        }
    }
    Surface {
        NavHost(navigator, "/select_login") {
            composable("/select_login") {
                SelectLoginPage(nav)
            }
            composable("/select_signup") {
                SelectSignupPage(nav)
            }
            composable("/input_private_key") {
                InputPrivateKeyPage()
            }
        }
    }
}

@Composable
fun SelectLoginPage(loginNav: LoginNav) {
    CenterBox {
        Column(verticalArrangement = Arrangement.spacedBy(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.sign_in), style = MaterialTheme.typography.headlineMedium)
            OutlinedButton({
                LoginViewModel.state.value = ClientSession.PrivateKeySignIn("")
                loginNav.gotoPrivateKey()
            }, shape = ButtonDefaults.outlinedShape) {
                Text(stringResource(Res.string.private_key))
            }
            Text(stringResource(Res.string.go_to_sign_up), modifier = Modifier.clickable {
                LoginViewModel.state.value = ClientSession.SignUpNone
                loginNav.gotoSignUp()
            }, textDecoration = TextDecoration.Underline)
        }
    }
}

@Composable
fun SelectSignupPage(loginNav: LoginNav) {
    CenterBox {
        Column(verticalArrangement = Arrangement.spacedBy(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.sign_up), style = MaterialTheme.typography.headlineMedium)
            Button({
                LoginViewModel.state.value = ClientSession.PrivateKeySignUp("")
                loginNav.gotoPrivateKey()
            }) {
                Text(stringResource(Res.string.private_key))
            }
            Text(stringResource(Res.string.go_to_sign_in), modifier = Modifier.clickable {
                loginNav.gotoLogin()
            }, textDecoration = TextDecoration.Underline)
        }
    }
}

@Composable
fun InputPrivateKeyPage() {
    val privateKey by LoginViewModel.inputtedPrivateKey.collectAsState("")
    val isSignUp by LoginViewModel.isSignUpFlow.collectAsState(false)
    val scope = rememberCoroutineScope()
    val appNav = LocalAppNav.current

    CenterBox {
        Column(modifier = Modifier.padding(20.dp)) {
            MeasureTextLineCount(privateKey, LocalTextStyle.current, 32.dp) { lineCount, _ ->
                OutlinedTextField(
                    privateKey,
                    onValueChange = {
                        LoginViewModel.updatePrivateKey(it)
                    },
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                    maxLines = max(lineCount, 2),
                    minLines = max(lineCount, 2),
                    label = {
                        Text(stringResource(Res.string.input_private_key))
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        startSign(privateKey, scope, appNav, isSignUp)
                    })
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button({
                    startSign(privateKey, scope, appNav, isSignUp)
                }) {
                    Text(
                        stringResource(
                            if (isSignUp) {
                                Res.string.start_sign_up
                            } else {
                                Res.string.start_sign_in
                            }
                        )
                    )
                }
                if (isSignUp) {
                    Button({
                        scope.launch {
                            LoginViewModel.updatePrivateKey(generateKeyPair())
                        }
                    }) {
                        Text(stringResource(Res.string.auto_generate))
                    }
                }
            }
        }
    }
}

private fun startSign(
    privateKey: String,
    scope: CoroutineScope,
    appNav: AppNav,
    isSignUp: Boolean
) {
    if (privateKey.isNotBlank()) {
        scope.launch {
            signUpOrSignIn(appNav, privateKey, isSignUp)
        }
    }
}

private suspend fun signUpOrSignIn(
    appNav: AppNav,
    privateKey: String,
    isSignUp: Boolean
) {
    globalDialogState.use(appNav::gotoHome) {
        val data = client.getData().getOrThrow()
        val f = finalData(data)
        val sig = signature(privateKey, f)
        val publicKey = getDerPublicKeyFromPrivateKey(privateKey)
        val ad = calcAddress(publicKey)
        val u = when {
            isSignUp -> client.signUp(publicKey, sig)
            else -> client.signIn(ad, sig)
        }.getOrThrow()
        LoginViewModel.updateState(ClientSession.SignUpSuccess(privateKey, publicKey, ad))
        LoginViewModel.updateSession(data, sig)
        LoginViewModel.updateUser(u)
        storeToStorage()
    }
}

@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
fun restoreFromStorage() {
    val loginUser = settings.decodeValueOrNull(LoginUser.serializer(), "login_user") ?: return
    val privateKey = loginUser.privateKey
    val publicKey = loginUser.publicKey
    val address = loginUser.address
    val signature = loginUser.signature
    val data = loginUser.data
    val userInfo = loginUser.user
    LoginViewModel.updateState(ClientSession.SignUpSuccess(privateKey, publicKey, address))
    LoginViewModel.updateUser(userInfo)
    LoginViewModel.updateSession(data, signature)
}

@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
fun storeToStorage() {
    val state = LoginViewModel.state.value as ClientSession.SignUpSuccess
    val session = LoginViewModel.session ?: return
    val user = LoginViewModel.user.value ?: return
    val loginUser = LoginUser(state.privateKey, state.publicKey, state.address, session.second, session.first, user)
    settings.encodeValue(LoginUser.serializer(), "login_user", loginUser)
}

interface LoginNav {

    fun gotoPrivateKey()

    fun gotoSignUp()

    fun gotoLogin()

    companion object {
        val DEFAULT = object : LoginNav {

            override fun gotoPrivateKey() {
                TODO("Not yet implemented")
            }

            override fun gotoSignUp() {
                TODO("Not yet implemented")
            }

            override fun gotoLogin() {
                TODO("Not yet implemented")
            }
        }
    }
}
