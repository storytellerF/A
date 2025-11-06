package com.storyteller_f.a.app.compose_app.pages.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.storyteller_f.a.app.compose_app.CustomUserSessionManager
import com.storyteller_f.a.app.compose_app.LocalAppNavFactory
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.auto_generate
import com.storyteller_f.a.app.compose_app.common.AppNavFactory
import com.storyteller_f.a.app.compose_app.common.SessionHistoryViewModel
import com.storyteller_f.a.app.compose_app.common.getLoginHistoryViewModel
import com.storyteller_f.a.app.compose_app.components.BaseSheet
import com.storyteller_f.a.app.compose_app.go_to_sign_in
import com.storyteller_f.a.app.compose_app.go_to_sign_up
import com.storyteller_f.a.app.compose_app.private_key
import com.storyteller_f.a.app.compose_app.sign_in
import com.storyteller_f.a.app.compose_app.sign_up
import com.storyteller_f.a.app.compose_app.start_sign_in
import com.storyteller_f.a.app.compose_app.start_sign_up
import com.storyteller_f.a.app.compose_app.utils.appPlatform
import com.storyteller_f.a.app.core.components.CenterBox
import com.storyteller_f.a.app.core.components.GlobalDialogController
import com.storyteller_f.a.app.core.components.LocalGlobalDialog
import com.storyteller_f.a.app.core.components.PrivateKeyInput
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.client.core.getUserInfo
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.replaceCrlf
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter
import org.jetbrains.compose.ui.tooling.preview.PreviewParameterProvider

@Composable
fun LoginPage() {
    val navigator = rememberNavController()
    val nav = remember {
        buildLoginNav(navigator)
    }
    val appNavFactory = LocalAppNavFactory.current
    Surface {
        Column {
            if (!appPlatform.hasNativeBack) {
                IconButton({
                    if (!navigator.popBackStack()) {
                        appNavFactory.newAppNav().back()
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
            if (!navigator.popBackStack("/signUp_input_private_key", false)) {
                navigator.navigate("/signUp_input_private_key")
            }
        } else {
            if (!navigator.popBackStack("/signIn_input_private_key", false)) {
                navigator.navigate("/signIn_input_private_key")
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

@OptIn(ExperimentalMaterial3Api::class)
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
                OutlinedButton(
                    {
                        loginNav.gotoPrivateKey(false)
                    },
                    shape = ButtonDefaults.outlinedShape,
                    modifier = Modifier.testTag("private_key")
                ) {
                    Text(stringResource(Res.string.private_key))
                }
                SelectFile(false)
                SelectFromHistory()
            }
            Text(stringResource(Res.string.go_to_sign_up), modifier = Modifier.clickable {
                loginNav.gotoSignUp()
            }.testTag("goto_sign_up"), textDecoration = TextDecoration.Underline)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SelectFromHistory() {
    val viewModel = getLoginHistoryViewModel()
    SelectFromHistoryInternal(viewModel)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SelectFromHistoryInternal(viewModel: SessionHistoryViewModel) {
    var showSheet by remember {
        mutableStateOf(false)
    }
    val sheetState = rememberModalBottomSheetState()
    OutlinedButton({
        showSheet = true
    }) {
        Text("Last Used")
    }
    BaseSheet(showSheet, sheetState, {
        showSheet = false
    }) {
        val data by viewModel.handler.data.collectAsState()
        val last = data?.history?.last
        val scope = rememberCoroutineScope()
        val appNavFactory = LocalAppNavFactory.current
        StateView(viewModel.handler, modifier = Modifier.height(200.dp)) {
            LazyColumn {
                items(it.alias) { alias ->
                    LoginHistoryCell(alias, last, {
                        scope.launch {
                            if (viewModel.getSession(alias)) {
                                showSheet = false
                                appNavFactory.newAppNav().gotoHome()
                            }
                        }
                    }, {
                        viewModel.deleteSession(alias)
                    })
                }
            }
        }
    }
}

class PrivateParameterProvider : PreviewParameterProvider<String> {
    override val values: Sequence<String>
        get() = sequence {
            yield(buildString {
                repeat(50) {
                    append("a")
                }
            })
        }
}

@Preview
@Composable
private fun LoginHistoryCell(
    @PreviewParameter(PrivateParameterProvider::class) address: String,
    last: String? = "hello",
    onSelect: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clickable {
        onSelect()
    }, verticalAlignment = Alignment.CenterVertically) {
        Text(
            address,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium
        )
        val shape = RoundedCornerShape(10.dp)
        if (address == last) {
            Text(
                "上次登录",
                modifier = Modifier.clip(shape)
                    .background(MaterialTheme.colorScheme.primaryContainer, shape)
                    .padding(8.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton({
            onDelete()
        }) {
            Icon(Icons.Default.Delete, "delete")
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
                }, modifier = Modifier.testTag("private_key")) {
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
    val appNavFactory = LocalAppNavFactory.current
    val sessionManager = LocalSessionManager.current
    val globalDialogController = LocalGlobalDialog.current
    if (isSignUp) {
        Button({
            scope.launch {
                startSignFromFile(appNavFactory, sessionManager, true, globalDialogController)
            }
        }) {
            Text("Select File")
        }
    } else {
        OutlinedButton({
            scope.launch {
                startSignFromFile(appNavFactory, sessionManager, false, globalDialogController)
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
    val appNavFactory = LocalAppNavFactory.current
    val sessionManager = LocalSessionManager.current
    val globalDialogController = LocalGlobalDialog.current
    val startSign: () -> Unit = {
        scope.launch {
            globalDialogController.startSign(
                appNavFactory,
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
                Button(startSign, modifier = Modifier.testTag("start_sign")) {
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
                            privateKey = getAlgo().generatePemKeyPair().getOrThrow().first
                        }
                    }, modifier = Modifier.testTag("auto_generate")) {
                        Text(stringResource(Res.string.auto_generate))
                    }
                }
            }
        }
    }
}

private suspend fun startSignFromFile(
    appNav: AppNavFactory,
    sessionManager: CustomUserSessionManager,
    isSignUp: Boolean,
    globalDialogController: GlobalDialogController,
) {
    val f = FileKit.openFilePicker()
    if (f != null) {
        val privateKey = String(f.readBytes()).replaceCrlf()
        globalDialogController.startSign(
            appNav,
            sessionManager,
            privateKey,
            isSignUp
        )
    }
}

private suspend fun GlobalDialogController.startSign(
    appNav: AppNavFactory,
    sessionManager: CustomUserSessionManager,
    privateKey: String,
    isSignUp: Boolean,
) {
    if (privateKey.isBlank()) return

    useResult {
        runCatching {
            sessionManager.getUserInfo(privateKey, isSignUp) {
                sessionManager.sessionHistoryManager.addSession(it)
            }
        }
    }.onSuccess {
        appNav.newAppNav().gotoHome()
    }
}

interface LoginNav {

    fun gotoPrivateKey(isSignUp: Boolean)

    fun gotoSignUp()

    fun gotoLogin()
}
