package com.storyteller_f.a.app.pages.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storyteller_f.a.app.AppGlobalDialogController
import com.storyteller_f.a.app.LocalAppNavFactory
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.auth_private_key_subtitle
import com.storyteller_f.a.app.common.AppNavFactory
import com.storyteller_f.a.app.core.components.PrivateKeyInput
import com.storyteller_f.a.app.core.components.request
import com.storyteller_f.a.app.sign_in
import com.storyteller_f.a.app.sign_up
import com.storyteller_f.a.app.start_sign_in
import com.storyteller_f.a.app.start_sign_up
import com.storyteller_f.a.app.utils.completeTotpSignIn
import com.storyteller_f.a.app.utils.startAuth
import com.storyteller_f.a.client.core.PendingTotpSignIn
import com.storyteller_f.a.client.core.UserAuthResult
import com.storyteller_f.shared.model.AlgoType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun PrivateKeyAuthPage(isSignUp: Boolean) {
    val viewModel = viewModel { InputPrivateKeyViewModel() }
    val privateKey by viewModel.privateKey.collectAsState()
    val encryptionPrivateKey by viewModel.encryptionPrivateKey.collectAsState()
    val address by viewModel.address.collectAsState()
    val algo by viewModel.algo.collectAsState()
    val scope = rememberCoroutineScope()
    val appNavFactory = LocalAppNavFactory.current
    val globalDialogController = LocalGlobalDialog.current
    var pendingTotp by remember { mutableStateOf<PendingTotpSignIn?>(null) }
    val startSign: () -> Unit = {
        scope.launch {
            val pending = globalDialogController.performAuth(
                appNavFactory,
                privateKey,
                encryptionPrivateKey,
                algo,
                isSignUp
            )
            if (pending != null) {
                pendingTotp = pending
            }
        }
    }

    PrivateKeyAuthContent(
        isSignUp = isSignUp,
        privateKey = privateKey,
        encryptionPrivateKey = encryptionPrivateKey,
        address = address,
        algo = algo,
        startSign = startSign,
        viewModel = viewModel,
    )
    TotpSignInDialog(pendingTotp, {
        pendingTotp = null
    }) { pending, code ->
        scope.completePendingTotpSignIn(globalDialogController, appNavFactory, pending, code) {
            pendingTotp = null
        }
    }
}

@Composable
private fun PrivateKeyAuthContent(
    isSignUp: Boolean,
    privateKey: String,
    encryptionPrivateKey: String?,
    address: String?,
    algo: AlgoType,
    startSign: () -> Unit,
    viewModel: InputPrivateKeyViewModel,
) {
    AuthPageChrome(
        title = stringResource(if (isSignUp) Res.string.sign_up else Res.string.sign_in),
        subtitle = stringResource(Res.string.auth_private_key_subtitle),
        footer = {}
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PrivateKeyInput(privateKey, encryptionPrivateKey, address, isSignUp, algo, {
                viewModel.updateAlgo(it)
            }, {
                viewModel.updatePrivateKey(it)
            }) {
                viewModel.updateEncryptionPrivateKey(it)
            }
            Button(
                onClick = startSign,
                modifier = Modifier.fillMaxWidth().testTag("start_sign"),
                shape = ButtonDefaults.shape
            ) {
                Text(stringResource(if (isSignUp) Res.string.start_sign_up else Res.string.start_sign_in))
            }
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.completePendingTotpSignIn(
    globalDialogController: AppGlobalDialogController,
    appNavFactory: AppNavFactory,
    pending: PendingTotpSignIn,
    code: String,
    onSuccess: () -> Unit,
) {
    launch {
        globalDialogController.useResult {
            request {
                completeTotpSignIn(pending, code)
                Result.success(Unit)
            }
        }.onSuccess {
            onSuccess()
            appNavFactory.newAppNav().gotoHome()
        }
    }
}

private suspend fun AppGlobalDialogController.performAuth(
    appNav: AppNavFactory,
    privateKey: String,
    encryptionPrivateKey: String?,
    algo: AlgoType,
    isSignUp: Boolean,
): PendingTotpSignIn? {
    if (privateKey.isBlank()) return null
    return useResult {
        request {
            when (val result = context.sessionManager.startAuth(privateKey, encryptionPrivateKey, algo, isSignUp)) {
                is UserAuthResult.Success -> Result.success<AuthResult>(AuthResult.Success)
                is UserAuthResult.RequiresTotp -> Result.success(AuthResult.RequiresTotp(result.pending))
            }
        }
    }.map { result ->
        when (result) {
            AuthResult.Success -> {
                appNav.newAppNav().gotoHome()
                null
            }

            is AuthResult.RequiresTotp -> result.pending
        }
    }.getOrNull()
}

private sealed class AuthResult {
    data object Success : AuthResult()
    data class RequiresTotp(val pending: PendingTotpSignIn) : AuthResult()
}

@Composable
private fun TotpSignInDialog(
    pending: PendingTotpSignIn?,
    onDismiss: () -> Unit,
    onConfirm: (PendingTotpSignIn, String) -> Unit,
) {
    var code by remember(pending) { mutableStateOf("") }
    if (pending != null) {
        AlertDialog(onDismiss, {
            Button({
                onConfirm(pending, code)
            }) {
                Text("OK")
            }
        }, title = {
            Text("Two-factor authentication")
        }, text = {
            OutlinedTextField(code, {
                code = it
            }, label = {
                Text("TOTP code")
            })
        })
    }
}
