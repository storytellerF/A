package com.storyteller_f.a.app.compose_app.pages.user

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
import com.storyteller_f.a.app.compose_app.*
import com.storyteller_f.a.app.compose_app.compontents.CenterBox
import com.storyteller_f.a.app.compose_app.compontents.GlobalDialogController
import com.storyteller_f.a.app.compose_app.compontents.MeasureTextLineCount
import com.storyteller_f.a.app.compose_app.utils.buildLoginUserSessionFactory
import com.storyteller_f.a.app.compose_app.utils.platform
import com.storyteller_f.a.client.core.ClientSessionState
import com.storyteller_f.a.client.core.signUpOrInFromPrivateKey
import com.storyteller_f.shared.generateECDSAPemPrivateKey
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.readBytes
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
                composable("/signUp_input_private_key") {
                    InputPrivateKeyPage(true)
                }
                composable("/signIn_input_private_key") {
                    InputPrivateKeyPage(false)
                }
            }
        }
    }
}

private fun buildLoginNav(navigator: NavHostController) = object : LoginNav {
    override fun gotoPrivateKey(isSignUp: Boolean) {
        if (isSignUp) {
            if (!navigator.popBackStack("/signIn_input_private_key", false)) {
                navigator.navigate("/signIn_input_private_key")
            }
        } else {
            if (!navigator.popBackStack("/signUp_input_private_key", false)) {
                navigator.navigate("/signUp_input_private_key")
            }
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
                    loginNav.gotoPrivateKey(false)
                }, shape = ButtonDefaults.outlinedShape) {
                    Text(stringResource(Res.string.private_key))
                }
                SelectFile(false)
            }
            Text(stringResource(Res.string.go_to_sign_up), modifier = Modifier.clickable {
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
                    loginNav.gotoPrivateKey(true)
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
    val sessionManager = LocalSessionManager.current
    val globalDialogController = LocalGlobalDialog.current
    if (isSignUp) {
        Button({
            scope.launch {
                startSignFromFile(appNav, sessionManager, true, globalDialogController)
            }
        }) {
            Text("Select File")
        }
    } else {
        OutlinedButton({
            scope.launch {
                startSignFromFile(appNav, sessionManager, false, globalDialogController)
            }
        }) {
            Text("Select File")
        }
    }
}

@Composable
fun InputPrivateKeyPage(isSignUp: Boolean) {
    var privateKey by remember {
        mutableStateOf("")
    }
    val scope = rememberCoroutineScope()
    val appNav = LocalAppNav.current
    val sessionManager = LocalSessionManager.current
    val globalDialogController = LocalGlobalDialog.current
    CenterBox {
        Column(modifier = Modifier.padding(20.dp)) {
            MeasureTextLineCount(privateKey, LocalTextStyle.current, 32.dp) { lineCount, _ ->
                OutlinedTextField(
                    privateKey,
                    onValueChange = {
                        privateKey = it
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
                            startSign(appNav, sessionManager, privateKey, isSignUp, globalDialogController)
                        }
                    })
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button({
                    scope.launch {
                        startSign(appNav, sessionManager, privateKey, isSignUp, globalDialogController)
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
                            privateKey = (generateECDSAPemPrivateKey().getOrThrow())
                        }
                    }) {
                        Text(stringResource(Res.string.auto_generate))
                    }
                }
            }
        }
    }
}

private suspend fun startSignFromFile(
    appNav: AppNav,
    sessionManager: CustomSessionManager,
    isSignUp: Boolean,
    globalDialogController: GlobalDialogController,
) {
    val f = FileKit.openFilePicker()
    if (f != null) {
        startSign(
            appNav,
            sessionManager,
            String(f.readBytes()).replace("\r\n", "\n"),
            isSignUp,
            globalDialogController
        )
    }
}

private suspend fun startSign(
    appNav: AppNav,
    sessionManager: CustomSessionManager,
    privateKey: String,
    isSignUp: Boolean,
    globalDialogController: GlobalDialogController,
) {
    if (privateKey.isNotBlank()) {
        if (signUpOrSignIn(privateKey, sessionManager, isSignUp, globalDialogController).isSuccess) {
            appNav.gotoHome()
        }
    }
}

suspend fun signUpOrSignIn(
    privateKey: String,
    sessionManager: CustomSessionManager,
    isSignUp: Boolean,
    globalDialogController: GlobalDialogController,
): Result<Unit?> {
    return globalDialogController.use {
        val (rawUserPassInfo) = signUpOrInFromPrivateKey(
            privateKey,
            sessionManager,
            isSignUp
        )
        val userSession = buildLoginUserSessionFactory(
            sessionManager.settings
        ).addSession(rawUserPassInfo)
        sessionManager.sessionModel.updateState(ClientSessionState.Success(userSession))
    }
}

interface LoginNav {

    fun gotoPrivateKey(isSignUp: Boolean)

    fun gotoSignUp()

    fun gotoLogin()
}
