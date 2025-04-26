package com.storyteller_f.a.app

import a.composeapp.generated.resources.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.storyteller_f.a.app.common.CenterBox
import com.storyteller_f.a.app.compontents.MeasureTextLineCount
import com.storyteller_f.a.app.utils.buildLoginUserSessionFactory
import com.storyteller_f.a.app.utils.platform
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.*
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.readBytes
import io.ktor.client.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max

@Composable
fun LoginPage() {
    val navigator = rememberNavController()
    val nav = remember {
        buildLoginNav(navigator)
    }
    val appNav = LocalAppNav.current
    Surface {
        Column {
            if (!platform.hasNativeBack) {
                IconButton({
                    if (!navigator.popBackStack()) {
                        appNav.back()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, "back to pre page")
                }
            }
            NavHost(navigator, "/select_signIn") {
                composable("/select_signIn") {
                    SelectSignInPage(nav)
                }
                composable("/select_signUp") {
                    SelectSignUpPage(nav)
                }
                composable("/input_private_key") {
                    InputPrivateKeyPage()
                }
            }
        }
    }
}

private fun buildLoginNav(navigator: NavHostController) = object : LoginNav {

    override fun gotoPrivateKey() {
        if (!navigator.popBackStack("/input_private_key", false)) {
            navigator.navigate("/input_private_key")
        }
    }

    override fun gotoSignUp() {
        if (!navigator.popBackStack("/select_signUp", false)) {
            navigator.navigate("/select_signUp")
        }
    }

    override fun gotoLogin() {
        if (!navigator.popBackStack("/select_signIn", false)) {
            navigator.navigate("/select_signIn")
        }
    }
}

@Composable
fun SelectSignInPage(loginNav: LoginNav) {
    CenterBox {
        Column(verticalArrangement = Arrangement.spacedBy(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.sign_in), style = MaterialTheme.typography.headlineMedium)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedButton({
                    SignInViewModel.state.value = ClientSession.PrivateKeySignIn("")
                    loginNav.gotoPrivateKey()
                }, shape = ButtonDefaults.outlinedShape) {
                    Text(stringResource(Res.string.private_key))
                }
                SelectFile(false)
            }
            Text(stringResource(Res.string.go_to_sign_up), modifier = Modifier.clickable {
                SignInViewModel.state.value = ClientSession.SignUpNone
                loginNav.gotoSignUp()
            }, textDecoration = TextDecoration.Underline)
        }
    }
}

@Composable
fun SelectSignUpPage(loginNav: LoginNav) {
    CenterBox {
        Column(verticalArrangement = Arrangement.spacedBy(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.sign_up), style = MaterialTheme.typography.headlineMedium)
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button({
                    SignInViewModel.state.value = ClientSession.PrivateKeySignUp("")
                    loginNav.gotoPrivateKey()
                }) {
                    Text(stringResource(Res.string.private_key))
                }
                SelectFile(true)
            }
            Text(stringResource(Res.string.go_to_sign_in), modifier = Modifier.clickable {
                loginNav.gotoLogin()
            }, textDecoration = TextDecoration.Underline)
        }
    }
}

@Composable
fun SelectFile(isSignUp: Boolean) {
    val scope = rememberCoroutineScope()
    val appNav = LocalAppNav.current
    val client = LocalClient.current
    if (isSignUp) {
        Button({
            scope.launch {
                startSignFromFile(appNav, client, true)
            }
        }) {
            Text("Select File")
        }
    } else {
        OutlinedButton({
            scope.launch {
                startSignFromFile(appNav, client, false)
            }
        }) {
            Text("Select File")
        }
    }
}

private suspend fun startSignFromFile(
    appNav: AppNav,
    client: HttpClient,
    isSignUp: Boolean
) {
    val f = FileKit.openFilePicker()
    if (f != null) {
        startSign(appNav, client, String(f.readBytes()).replace("\r\n", "\n"), isSignUp)
    }
}

@Composable
fun InputPrivateKeyPage() {
    val privateKey by SignInViewModel.inputtedPrivateKey.collectAsState("")
    val isSignUp by SignInViewModel.isSignUpFlow.collectAsState(false)
    val scope = rememberCoroutineScope()
    val appNav = LocalAppNav.current
    val client = LocalClient.current
    CenterBox {
        Column(modifier = Modifier.padding(20.dp)) {
            MeasureTextLineCount(privateKey, LocalTextStyle.current, 32.dp) { lineCount, _ ->
                OutlinedTextField(
                    privateKey,
                    onValueChange = {
                        SignInViewModel.updatePrivateKey(it)
                    },
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                    maxLines = max(lineCount, 2),
                    minLines = max(lineCount, 2),
                    label = {
                        Text(stringResource(Res.string.input_private_key))
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        scope.launch {
                            startSign(appNav, client, privateKey, isSignUp)
                        }
                    })
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button({
                    scope.launch {
                        startSign(appNav, client, privateKey, isSignUp)
                    }
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
                            SignInViewModel.updatePrivateKey(generateECDSAPemPrivateKey())
                        }
                    }) {
                        Text(stringResource(Res.string.auto_generate))
                    }
                }
            }
        }
    }
}

private suspend fun startSign(
    appNav: AppNav,
    client: HttpClient,
    privateKey: String,
    isSignUp: Boolean
) {
    if (privateKey.isNotBlank()) {
        if (signUpOrSignIn(privateKey, client, isSignUp).isSuccess) {
            appNav.gotoHome()
        }
    }
}

suspend fun signUpOrSignIn(
    privateKey: String,
    client: HttpClient,
    isSignUp: Boolean,
): Result<Unit?> {
    return globalDialogState.use {
        val data = client.getData().getOrThrow()
        val f = finalData(data)
        val sig = signature(privateKey, f)
        val publicKey = getDerPublicKeyFromPrivateKey(privateKey)
        val ad = calcAddress(publicKey)
        val u = when {
            isSignUp -> client.signUp(publicKey, sig)
            else -> client.signIn(ad, sig)
        }.getOrThrow()
        val userSession = buildLoginUserSessionFactory().addSession(LoginUser(privateKey, publicKey, ad))
        SignInViewModel.updateUser(u)
        SignInViewModel.updateState(ClientSession.SignInSuccess(userSession))
        SignInViewModel.updateSession(data, sig)
    }
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
