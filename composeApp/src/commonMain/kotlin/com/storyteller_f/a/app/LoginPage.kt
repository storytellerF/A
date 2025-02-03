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
import com.storyteller_f.a.app.utils.LoginUser
import com.storyteller_f.a.app.utils.buildLoginUserSessionFactory
import com.storyteller_f.a.app.utils.platform
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.*
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.extension
import io.github.vinceglb.filekit.core.pickFile
import io.ktor.client.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

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
}

private fun buildLoginNav(navigator: NavHostController) = object : LoginNav {

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

@Composable
fun SelectLoginPage(loginNav: LoginNav) {
    CenterBox {
        Column(verticalArrangement = Arrangement.spacedBy(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.sign_in), style = MaterialTheme.typography.headlineMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({
                    LoginViewModel.state.value = ClientSession.PrivateKeySignIn("")
                    loginNav.gotoPrivateKey()
                }, shape = ButtonDefaults.outlinedShape) {
                    Text(stringResource(Res.string.private_key))
                }
                SelectFile(false)
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button({
                    LoginViewModel.state.value = ClientSession.PrivateKeySignUp("")
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
    val toasterState = LocalToaster.current
    OutlinedButton({
        scope.launch {
            val f = FileKit.pickFile()
            if (f != null) {
                if (f.extension == "txt") {
                    startSign(String(f.readBytes()), appNav, client, isSignUp)
                } else {
                    toasterState.show("invalid file ${f.extension}", duration = 1.seconds)
                }
            }
        }
    }) {
        Text("Select File")
    }
}

@Composable
fun InputPrivateKeyPage() {
    val privateKey by LoginViewModel.inputtedPrivateKey.collectAsState("")
    val isSignUp by LoginViewModel.isSignUpFlow.collectAsState(false)
    val scope = rememberCoroutineScope()
    val appNav = LocalAppNav.current
    val client = LocalClient.current
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
                        scope.launch {
                            startSign(privateKey, appNav, client, isSignUp)
                        }
                    })
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button({
                    scope.launch {
                        startSign(privateKey, appNav, client, isSignUp)
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

private suspend fun startSign(
    privateKey: String,
    appNav: AppNav,
    client: HttpClient,
    isSignUp: Boolean
) {
    if (privateKey.isNotBlank()) {
        signUpOrSignIn(privateKey, client, isSignUp, appNav::gotoHome) {}
    }
}

suspend fun signUpOrSignIn(
    privateKey: String,
    client: HttpClient,
    isSignUp: Boolean,
    done: () -> Unit,
    onError: () -> Unit
) {
    globalDialogState.use(done, onError) {
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
        LoginViewModel.updateUser(u)
        LoginViewModel.updateState(ClientSession.SignUpSuccess(userSession))
        LoginViewModel.updateSession(data, sig)
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
