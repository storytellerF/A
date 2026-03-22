package com.storyteller_f.a.app.pages.user

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.storyteller_f.a.app.AppGlobalDialogController
import com.storyteller_f.a.app.LocalAppNavFactory
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.back_to_pre_page
import com.storyteller_f.a.app.common.AppNavFactory
import com.storyteller_f.a.app.common.SessionHistoryViewModel
import com.storyteller_f.a.app.common.getLoginHistoryViewModel
import com.storyteller_f.a.app.core.components.BaseSheet
import com.storyteller_f.a.app.core.components.CenterBox
import com.storyteller_f.a.app.core.components.PrivateKeyInput
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.request
import com.storyteller_f.a.app.delete
import com.storyteller_f.a.app.go_to_sign_in
import com.storyteller_f.a.app.go_to_sign_up
import com.storyteller_f.a.app.last_used
import com.storyteller_f.a.app.private_key
import com.storyteller_f.a.app.sign_in
import com.storyteller_f.a.app.sign_up
import com.storyteller_f.a.app.start_sign_in
import com.storyteller_f.a.app.start_sign_up
import com.storyteller_f.a.app.utils.appPlatform
import com.storyteller_f.a.app.utils.fetchAndSaveUserInfo
import com.storyteller_f.shared.model.AlgoType
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.stringResource

@Serializable
data object SignIn : NavKey

@Serializable
data object SignUp : NavKey

@Serializable
data object SignUpInput : NavKey

@Serializable
data object SignInInput : NavKey

@Composable
fun SignInAndSignUpPage() {
    val config = SavedStateConfiguration {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                subclass(SignIn::class, SignIn.serializer())
                subclass(SignUp::class, SignUp.serializer())
                subclass(SignUpInput::class, SignUpInput.serializer())
                subclass(SignInInput::class, SignInInput.serializer())
            }
        }
    }
    val backStack = rememberNavBackStack(config, SignIn)
    val nav = remember {
        buildSignInAndSignUpNav(backStack)
    }
    val appNavFactory = LocalAppNavFactory.current
    Surface {
        Column {
            if (!appPlatform.hasNativeBack) {
                IconButton({
                    if (backStack.size > 1) {
                        appNavFactory.newAppNav().back()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(Res.string.back_to_pre_page))
                }
            }
            SignInNavDisplay(backStack, nav)
        }
    }
}

@Composable
private fun SignInNavDisplay(backStack: NavBackStack<NavKey>, nav: SignInAndSignUpNav) {
    NavDisplay(
        backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        entryProvider = entryProvider {
            entry<SignIn> {
                SelectSignInPage(nav)
            }
            entry<SignUp> {
                SelectSignUpPage(nav)
            }
            entry<SignUpInput> {
                InputPrivateKeyPage(true)
            }
            entry<SignInInput> {
                InputPrivateKeyPage(false)
            }
        }
    )
}

private fun buildSignInAndSignUpNav(backStack: NavBackStack<NavKey>) = object : SignInAndSignUpNav {
    override fun gotoPrivateKey(isSignUp: Boolean) {
        if (isSignUp) {
            val i = backStack.indexOf(SignUpInput)
            if (i >= 0) {
                repeat(backStack.size - i - 1) {
                    backStack.removeLastOrNull()
                }
            }
            if (backStack.last() !is SignUpInput) {
                backStack.add(SignUpInput)
            }
        } else {
            val i = backStack.indexOf(SignInInput)
            if (i >= 0) {
                repeat(backStack.size - i - 1) {
                    backStack.removeLastOrNull()
                }
            }
            if (backStack.last() !is SignInInput) {
                backStack.add(SignInInput)
            }
        }
    }

    override fun gotoSignUp() {
        val i = backStack.indexOf(SignUp)
        if (i >= 0) {
            repeat(backStack.size - i - 1) {
                backStack.removeLastOrNull()
            }
        }
        if (backStack.last() !is SignUp) {
            backStack.add(SignUp)
        }
    }

    override fun gotoSignIn() {
        val i = backStack.indexOf(SignIn)
        if (i >= 0) {
            repeat(backStack.size - i - 1) {
                backStack.removeLastOrNull()
            }
        }
        if (backStack.last() !is SignIn) {
            backStack.add(SignIn)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectSignInPage(signInAndSignUpNav: SignInAndSignUpNav) {
    CenterBox {
        Column(
            verticalArrangement = Arrangement.spacedBy(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(Res.string.sign_in), style = MaterialTheme.typography.headlineMedium)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedButton(
                    {
                        signInAndSignUpNav.gotoPrivateKey(false)
                    },
                    shape = ButtonDefaults.outlinedShape,
                    modifier = Modifier.testTag("private_key")
                ) {
                    Text(stringResource(Res.string.private_key))
                }

                SelectFromHistory()
            }
            Text(stringResource(Res.string.go_to_sign_up), modifier = Modifier.clickable {
                signInAndSignUpNav.gotoSignUp()
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
        Text(stringResource(Res.string.last_used))
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
                stringResource(Res.string.last_used),
                modifier = Modifier.clip(shape)
                    .background(MaterialTheme.colorScheme.primaryContainer, shape)
                    .padding(8.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton({
            onDelete()
        }) {
            Icon(Icons.Default.Delete, stringResource(Res.string.delete))
        }
    }
}

@Composable
fun SelectSignUpPage(signInAndSignUpNav: SignInAndSignUpNav) {
    CenterBox {
        Column(
            verticalArrangement = Arrangement.spacedBy(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(Res.string.sign_up), style = MaterialTheme.typography.headlineMedium)
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button({
                    signInAndSignUpNav.gotoPrivateKey(true)
                }, modifier = Modifier.testTag("private_key")) {
                    Text(stringResource(Res.string.private_key))
                }
            }
            Text(stringResource(Res.string.go_to_sign_in), modifier = Modifier.clickable {
                signInAndSignUpNav.gotoSignIn()
            }, textDecoration = TextDecoration.Underline)
        }
    }
}

@Composable
fun InputPrivateKeyPage(isSignUp: Boolean) {
    val viewModel = viewModel { InputPrivateKeyViewModel() }
    val privateKey by viewModel.privateKey.collectAsState()
    val encryptionPrivateKey by viewModel.encryptionPrivateKey.collectAsState()
    val address by viewModel.address.collectAsState()
    val algo by viewModel.algo.collectAsState()
    val scope = rememberCoroutineScope()
    val appNavFactory = LocalAppNavFactory.current
    val globalDialogController = LocalGlobalDialog.current
    val startSign: () -> Unit = {
        scope.launch {
            globalDialogController.performAuth(appNavFactory, privateKey, encryptionPrivateKey, algo, isSignUp)
        }
    }
    CenterBox {
        Column(modifier = Modifier.padding(20.dp)) {
            PrivateKeyInput(privateKey, encryptionPrivateKey, address, isSignUp, algo, {
                viewModel.updateAlgo(it)
            }, {
                viewModel.updatePrivateKey(it)
            }) {
                viewModel.updateEncryptionPrivateKey(it)
            }
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
        }
    }
}

private suspend fun AppGlobalDialogController.performAuth(
    appNav: AppNavFactory,
    privateKey: String,
    encryptionPrivateKey: String?,
    algo: AlgoType,
    isSignUp: Boolean,
) {
    if (privateKey.isBlank()) return
    useResult {
        request {
            val userInfo = fetchAndSaveUserInfo(privateKey, encryptionPrivateKey, algo, isSignUp)
            Result.success(userInfo)
        }
    }.onSuccess {
        appNav.newAppNav().gotoHome()
    }
}

interface SignInAndSignUpNav {

    fun gotoPrivateKey(isSignUp: Boolean)

    fun gotoSignUp()

    fun gotoSignIn()
}
