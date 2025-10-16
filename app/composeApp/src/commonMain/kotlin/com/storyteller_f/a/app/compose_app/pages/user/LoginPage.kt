package com.storyteller_f.a.app.compose_app.pages.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.storyteller_f.a.app.compose_app.AppNav
import com.storyteller_f.a.app.compose_app.CustomUserSessionManager
import com.storyteller_f.a.app.compose_app.LocalAppNav
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.auto_generate
import com.storyteller_f.a.app.compose_app.compontents.GlobalDialogController
import com.storyteller_f.a.app.compose_app.go_to_sign_in
import com.storyteller_f.a.app.compose_app.go_to_sign_up
import com.storyteller_f.a.app.compose_app.private_key
import com.storyteller_f.a.app.compose_app.sign_in
import com.storyteller_f.a.app.compose_app.sign_up
import com.storyteller_f.a.app.compose_app.start_sign_in
import com.storyteller_f.a.app.compose_app.start_sign_up
import com.storyteller_f.a.app.compose_app.utils.appPlatform
import com.storyteller_f.a.app.core.compontents.CenterBox
import com.storyteller_f.a.app.core.compontents.PrivateKeyInput
import com.storyteller_f.a.app.core.utils.buildLoginHistoryFactory
import com.storyteller_f.a.client.core.getUserInfo
import com.storyteller_f.shared.generateECDSAPemPrivateKey
import com.storyteller_f.shared.model.UserInfo
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoginPage() {
    val navigator = rememberNavController()
    val nav = remember {
        buildLoginNav(navigator)
    }
    val appNav = LocalAppNav.current
    Surface {
        Column {
            if (!appPlatform.hasNativeBack) {
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
        Column(
            verticalArrangement = Arrangement.spacedBy(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(Res.string.sign_in),
                style = MaterialTheme.typography.headlineMedium
            )
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
        Column(
            verticalArrangement = Arrangement.spacedBy(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(Res.string.sign_up),
                style = MaterialTheme.typography.headlineMedium
            )
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
    val startSign: () -> Unit = {
        scope.launch {
            globalDialogController.startSign(
                appNav,
                sessionManager,
                privateKey,
                isSignUp
            )
        }
    }
    CenterBox {
        Column(modifier = Modifier.padding(20.dp)) {
            PrivateKeyInput(privateKey, {
                privateKey = it
            }, startSign)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(startSign) {
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
                            privateKey = generateECDSAPemPrivateKey().getOrThrow()
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
    sessionManager: CustomUserSessionManager,
    isSignUp: Boolean,
    globalDialogController: GlobalDialogController,
) {
    val f = FileKit.openFilePicker()
    if (f != null) {
        val privateKey = String(f.readBytes()).replace("\r\n", "\n")
        globalDialogController.startSign(
            appNav,
            sessionManager,
            privateKey,
            isSignUp
        )
    }
}

private suspend fun GlobalDialogController.startSign(
    appNav: AppNav,
    sessionManager: CustomUserSessionManager,
    privateKey: String,
    isSignUp: Boolean,
) {
    if (privateKey.isNotBlank()) {
        if (signUpOrSignIn(privateKey, sessionManager, isSignUp).isSuccess) {
            appNav.gotoHome()
        }
    }
}

suspend fun GlobalDialogController.signUpOrSignIn(
    privateKey: String,
    sessionManager: CustomUserSessionManager,
    isSignUp: Boolean,
): Result<UserInfo> {
    return useResult {
        runCatching {
            sessionManager.getUserInfo(privateKey, isSignUp) {
                buildLoginHistoryFactory(sessionManager.settings).addSession(it)
            }
        }
    }
}

interface LoginNav {

    fun gotoPrivateKey(isSignUp: Boolean)

    fun gotoSignUp()

    fun gotoLogin()
}
