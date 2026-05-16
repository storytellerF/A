package com.storyteller_f.a.panel.common

import PanelFilePreviewPage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.storyteller_f.a.app.core.components.CenterBox
import com.storyteller_f.a.app.core.components.SignInButton
import com.storyteller_f.a.app.core.components.safeArea
import com.storyteller_f.a.panel.LocalPanelNav
import com.storyteller_f.a.panel.Res
import com.storyteller_f.a.panel.input
import com.storyteller_f.a.panel.pages.AllCommunitiesPage
import com.storyteller_f.a.panel.pages.AllFilesPage
import com.storyteller_f.a.panel.pages.AllPrivateRoomsPage
import com.storyteller_f.a.panel.pages.AllPublicRoomsPage
import com.storyteller_f.a.panel.pages.AllTitlesPage
import com.storyteller_f.a.panel.pages.AllTopicsPage
import com.storyteller_f.a.panel.pages.AllUsersPage
import com.storyteller_f.a.panel.pages.CommunityDetailPage
import com.storyteller_f.a.panel.pages.FileDetailPage
import com.storyteller_f.a.panel.pages.OverviewPage
import com.storyteller_f.a.panel.pages.PanelInputPage
import com.storyteller_f.a.panel.pages.RoomDetailPage
import com.storyteller_f.a.panel.pages.TaskRecordsPage
import com.storyteller_f.a.panel.pages.TitleDetailPage
import com.storyteller_f.a.panel.pages.TopicDetailPage
import com.storyteller_f.a.panel.pages.UserDetailPage
import com.storyteller_f.a.panel.panelAccountInstance
import com.storyteller_f.a.panel.sign_in
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.compose.resources.stringResource

@Serializable
data object LoginSelectScreen : NavKey

@Serializable
data object LoginInputScreen : NavKey

@Serializable
data class PanelUserDetailScreen(val uid: Long) : NavKey

@Serializable
data class PanelCommunityDetailScreen(val id: Long) : NavKey

@Serializable
data class PanelRoomDetailScreen(val id: Long) : NavKey

@Serializable
data class PanelTopicDetailScreen(val id: Long) : NavKey

@Serializable
data class PanelFileDetailScreen(val id: Long) : NavKey

@Serializable
data class PanelFilePreviewScreen(val id: Long) : NavKey

@Serializable
data class PanelTitleDetailScreen(val id: Long) : NavKey

@Serializable
data object PanelLoginScreen : NavKey

@Serializable
data object PanelOverviewScreen : NavKey

@Serializable
data object PanelAllUsersScreen : NavKey

@Serializable
data object PanelAllCommunitiesScreen : NavKey

@Serializable
data object PanelAllPublicRoomsScreen : NavKey

@Serializable
data object PanelAllPrivateRoomsScreen : NavKey

@Serializable
data object PanelAllTopicsScreen : NavKey

@Serializable
data object PanelAllFilesScreen : NavKey

@Serializable
data object PanelAllTitlesScreen : NavKey

@Serializable
data object PanelTaskRecordsScreen : NavKey

val panelNavSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(PanelUserDetailScreen::class)
        subclass(PanelCommunityDetailScreen::class)
        subclass(PanelRoomDetailScreen::class)
        subclass(PanelTopicDetailScreen::class)
        subclass(PanelFileDetailScreen::class)
        subclass(PanelFilePreviewScreen::class)
        subclass(PanelTitleDetailScreen::class)
        subclass(PanelLoginScreen::class)
        subclass(PanelOverviewScreen::class)
        subclass(PanelAllUsersScreen::class)
        subclass(PanelAllCommunitiesScreen::class)
        subclass(PanelAllPublicRoomsScreen::class)
        subclass(PanelAllPrivateRoomsScreen::class)
        subclass(PanelAllTopicsScreen::class)
        subclass(PanelAllFilesScreen::class)
        subclass(PanelAllTitlesScreen::class)
        subclass(PanelTaskRecordsScreen::class)
    }
}

interface PanelNav {
    val drawerState: DrawerState

    val backStack: NavBackStack<NavKey>
    fun gotoLogin()
    fun gotoOverview()
    fun gotoAllUsers()
    fun gotoUserDetail(uid: Long)
    fun gotoAllCommunities()
    fun gotoCommunityDetail(id: Long)
    fun gotoAllPublicRooms()
    fun gotoRoomDetail(id: Long)
    fun gotoAllPrivateRooms()
    fun gotoAllTopics()
    fun gotoTopicDetail(id: Long)
    fun gotoAllFiles()
    fun gotoFileDetail(id: Long)
    fun gotoAllTitles()
    fun gotoTitleDetail(id: Long)
    fun gotoTaskRecords()
    fun gotoFilePreview(id: Long, url: String, contentType: String, name: String)
    fun back()
    fun open()
}

interface PanelNavFactory {
    fun newPanelNav(): PanelNav

    companion object {
        val EMPTY = object : PanelNavFactory {
            override fun newPanelNav(): PanelNav {
                error("no panel nav")
            }
        }
    }
}

fun newPanelNav(backStack: NavBackStack<NavKey>, drawerState: DrawerState, scope: CoroutineScope) = object : PanelNav {
    override val drawerState: DrawerState
        get() = drawerState
    override val backStack: NavBackStack<NavKey>
        get() = backStack

    override fun gotoLogin() {
        backStack.add(PanelLoginScreen)
    }

    override fun gotoOverview() {
        backStack.add(PanelOverviewScreen)
    }

    override fun gotoAllUsers() {
        backStack.add(PanelAllUsersScreen)
    }

    override fun gotoUserDetail(uid: Long) {
        backStack.add(PanelUserDetailScreen(uid))
    }

    override fun gotoAllCommunities() {
        backStack.add(PanelAllCommunitiesScreen)
    }

    override fun gotoCommunityDetail(id: Long) {
        backStack.add(PanelCommunityDetailScreen(id))
    }

    override fun gotoAllPublicRooms() {
        backStack.add(PanelAllPublicRoomsScreen)
    }

    override fun gotoRoomDetail(id: Long) {
        backStack.add(PanelRoomDetailScreen(id))
    }

    override fun gotoAllPrivateRooms() {
        backStack.add(PanelAllPrivateRoomsScreen)
    }

    override fun gotoAllTopics() {
        backStack.add(PanelAllTopicsScreen)
    }

    override fun gotoTopicDetail(id: Long) {
        backStack.add(PanelTopicDetailScreen(id))
    }

    override fun gotoAllFiles() {
        backStack.add(PanelAllFilesScreen)
    }

    override fun gotoFileDetail(id: Long) {
        backStack.add(PanelFileDetailScreen(id))
    }

    override fun gotoAllTitles() {
        backStack.add(PanelAllTitlesScreen)
    }

    override fun gotoTitleDetail(id: Long) {
        backStack.add(PanelTitleDetailScreen(id))
    }

    override fun gotoTaskRecords() {
        backStack.add(PanelTaskRecordsScreen)
    }

    override fun gotoFilePreview(id: Long, url: String, contentType: String, name: String) {
        backStack.add(PanelFilePreviewScreen(id))
    }

    override fun back() {
        backStack.removeLastOrNull()
    }

    override fun open() {
        scope.launch {
            drawerState.open()
        }
    }
}

fun rootEntryProvider(nav: PanelNav) = entryProvider {
    entry<PanelLoginScreen> {
        PanelLoginPage {
            nav.back()
            nav.gotoOverview()
        }
    }
    entry<PanelOverviewScreen> {
        PanelHost {
            OverviewPage()
        }
    }
    entry<PanelAllUsersScreen> {
        AllUsersPage()
    }
    entry<PanelUserDetailScreen> {
        UserDetailPage(it.uid)
    }
    entry<PanelAllCommunitiesScreen> {
        AllCommunitiesPage()
    }
    entry<PanelCommunityDetailScreen> {
        CommunityDetailPage(it.id)
    }
    entry<PanelAllPublicRoomsScreen> {
        AllPublicRoomsPage()
    }
    entry<PanelRoomDetailScreen> {
        RoomDetailPage(it.id)
    }
    entry<PanelAllPrivateRoomsScreen> {
        AllPrivateRoomsPage()
    }
    entry<PanelAllTopicsScreen> {
        AllTopicsPage()
    }
    entry<PanelTopicDetailScreen> {
        TopicDetailPage(it.id)
    }
    entry<PanelAllFilesScreen> {
        AllFilesPage()
    }
    entry<PanelFileDetailScreen> {
        FileDetailPage(it.id)
    }
    entry<PanelAllTitlesScreen> {
        AllTitlesPage()
    }
    entry<PanelTitleDetailScreen> {
        TitleDetailPage(it.id)
    }
    entry<PanelTaskRecordsScreen> {
        TaskRecordsPage()
    }
    entry<PanelFilePreviewScreen> {
        PanelFilePreviewPage(it.id)
    }
}

@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
private fun PanelHost(content: @Composable () -> Unit) {
    val panelNav = LocalPanelNav.current
    val session = panelAccountInstance.sessionManager
    val user by session.isAlreadySignIn.collectAsState()
    Scaffold {
        if (user) {
            content()
        } else {
            CenterBox {
                SignInButton {
                    panelNav.gotoLogin()
                }
            }
        }
    }
}

@Composable
fun PanelLoginPage(back: () -> Unit) {
    val module = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(LoginSelectScreen::class, LoginSelectScreen.serializer())
            subclass(LoginInputScreen::class, LoginInputScreen.serializer())
        }
    }
    val config = remember {
        SavedStateConfiguration {
            serializersModule = module
        }
    }
    val backStack = rememberNavBackStack(config, LoginSelectScreen)
    Scaffold {
        val direction = LocalLayoutDirection.current
        Box(Modifier.safeArea(it, direction)) {
            NavDisplay(
                backStack,
                entryProvider = entryProvider {
                    entry<LoginSelectScreen> {
                        PanelSelectLoginPage { backStack.add(LoginInputScreen) }
                    }
                    entry<LoginInputScreen> {
                        PanelInputPage(back)
                    }
                }
            )
        }
    }
}

@Composable
private fun PanelSelectLoginPage(gotoInput: () -> Unit) {
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
                    gotoInput,
                    shape = ButtonDefaults.outlinedShape
                ) {
                    Text(stringResource(Res.string.input))
                }
            }
        }
    }
}
