package com.storyteller_f.a.app.compose_app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.rememberLibraries
import com.storyteller_f.a.app.compose_app.pages.HomePage
import com.storyteller_f.a.app.compose_app.pages.PreferencePage
import com.storyteller_f.a.app.compose_app.pages.community.CommunityComposePage
import com.storyteller_f.a.app.compose_app.pages.community.CommunityPage
import com.storyteller_f.a.app.compose_app.pages.community.CommunitySettingPage
import com.storyteller_f.a.app.compose_app.pages.media.MediaPage
import com.storyteller_f.a.app.compose_app.pages.room.RoomComposePage
import com.storyteller_f.a.app.compose_app.pages.room.RoomPage
import com.storyteller_f.a.app.compose_app.pages.room.RoomSettingPage
import com.storyteller_f.a.app.compose_app.pages.title.TitleComposePage
import com.storyteller_f.a.app.compose_app.pages.topic.ReactionListPage
import com.storyteller_f.a.app.compose_app.pages.topic.TopicComposePage
import com.storyteller_f.a.app.compose_app.pages.topic.TopicPage
import com.storyteller_f.a.app.compose_app.pages.user.LoginPage
import com.storyteller_f.a.app.compose_app.pages.user.MemberPage
import com.storyteller_f.a.app.compose_app.pages.user.UserPage
import com.storyteller_f.a.app.compose_app.pages.user.UserSettingPage
import com.storyteller_f.shared.commonJson
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.reflect.KClass

@Serializable
data object HomeScreen

@Serializable
data class CommunityScreen(val communityId: PrimaryKey, val showDialog: Boolean)

@Serializable
data class RoomScreen(val roomId: PrimaryKey, val showDialog: Boolean)

@Serializable
data object LoginScreen

@Serializable
data class TopicScreen(val topicId: PrimaryKey)

@Serializable
data object AboutScreen

@Serializable
data class UserScreen(val uid: PrimaryKey)

@Serializable
data class TopicComposeScreen(
    val objectType: String,
    val objectId: PrimaryKey,
    val enableExperimental: Boolean,
    val privateRoomId: PrimaryKey?,
    val communityId: PrimaryKey?,
)

@Serializable
data class MemberScreen(val objectType: String, val objectId: PrimaryKey)

@Serializable
data object UserSettingScreen

@Serializable
data object PreferenceScreen

@Serializable
data class MediaScreen(val json: String)

@Serializable
data object TitleComposeScreen

@Serializable
data object CommunityComposeScreen

@Serializable
data object RoomComposeScreen

@Serializable
data class CommunitySettingScreen(val communityId: PrimaryKey)

@Serializable
data class RoomSettingScreen(val roomId: PrimaryKey)

@Serializable
data class ReactionListScreen(val topicId: PrimaryKey)

inline fun <reified T : Any> AppNav.toRoute(): T? {
    if (!hasRoute(T::class)) return null
    return currentDestination?.toRoute<T>()
}

inline fun <reified T : Any> AppNav.hasRouteFlow(crossinline block: (T) -> Boolean = { true }): Flow<Boolean> {
    return currentDestinationFlow.filterNotNull().map {
        it.destination.hasRoute<T>() && block(it.toRoute<T>())
    }
}

interface AppNav {
    val currentDestination: NavBackStackEntry?

    val currentDestinationFlow: StateFlow<NavBackStackEntry?>

    fun <T : Any> hasRoute(any: KClass<T>): Boolean {
        return currentDestination?.destination?.hasRoute(any) == true
    }

    fun gotoLogin()

    fun gotoRoom(roomId: PrimaryKey, showDialog: Boolean)

    fun gotoCommunity(communityId: PrimaryKey, showDialog: Boolean)

    fun gotoTopic(topicId: PrimaryKey)

    fun gotoHome()

    fun gotoTopicCompose(
        objectType: ObjectType,
        objectId: PrimaryKey,
        enableExperimental: Boolean,
        privateRoomId: PrimaryKey?,
        communityId: PrimaryKey?,
    )

    fun gotoMemberPage(objectId: PrimaryKey, objectType: ObjectType)

    fun gotoAbout()

    fun gotoUser(uid: PrimaryKey)

    fun back()

    fun gotoUserSetting()

    fun gotoPreference()

    fun gotoMedia(info: FileInfo)

    fun gotoLocalImage(url: String)

    fun gotoTitleCompose()

    fun gotoCommunityCompose()

    fun gotoRoomCompose()

    fun gotoSettingPage(objectId: PrimaryKey, objectType: ObjectType)

    fun gotoReactionListPage(topicId: PrimaryKey)
}

fun newAppNav(navigator: NavHostController, scope: CoroutineScope) = object : AppNav {
    override val currentDestination: NavBackStackEntry?
        get() = navigator.currentBackStackEntry
    override val currentDestinationFlow: StateFlow<NavBackStackEntry?>
        get() = navigator.currentBackStackEntryFlow.stateIn(scope, SharingStarted.Eagerly, null)

    override fun gotoLogin() {
        navigator.navigate(route = LoginScreen)
    }

    override fun gotoRoom(roomId: PrimaryKey, showDialog: Boolean) {
        navigator.navigate(route = RoomScreen(roomId, showDialog))
    }

    override fun gotoCommunity(communityId: PrimaryKey, showDialog: Boolean) {
        navigator.navigate(route = CommunityScreen(communityId, showDialog))
    }

    override fun gotoTopic(topicId: PrimaryKey) {
        navigator.navigate(route = TopicScreen(topicId))
    }

    override fun gotoHome() {
        navigator.popBackStack(HomeScreen, false)
    }

    override fun gotoTopicCompose(
        objectType: ObjectType,
        objectId: PrimaryKey,
        enableExperimental: Boolean,
        privateRoomId: PrimaryKey?,
        communityId: PrimaryKey?,
    ) {
        navigator.navigate(
            TopicComposeScreen(
                objectType.name,
                objectId,
                enableExperimental,
                privateRoomId,
                communityId
            )
        )
    }

    override fun gotoMemberPage(
        objectId: PrimaryKey,
        objectType: ObjectType,
    ) {
        navigator.navigate(MemberScreen(objectType.name, objectId))
    }

    override fun gotoAbout() {
        navigator.navigate(AboutScreen)
    }

    override fun gotoUser(uid: PrimaryKey) {
        navigator.navigate(UserScreen(uid))
    }

    override fun back() {
        navigator.popBackStack()
    }

    override fun gotoUserSetting() {
        navigator.navigate(UserSettingScreen)
    }

    override fun gotoPreference() {
        navigator.navigate(PreferenceScreen)
    }

    override fun gotoMedia(info: FileInfo) {
        val route = MediaScreen(commonJson.encodeToString<MultiMediaInfo>(MultiMediaInfo.Image(info)))
        navigator.navigate(route)
    }

    override fun gotoLocalImage(url: String) {
        val route = MediaScreen(commonJson.encodeToString<MultiMediaInfo>(MultiMediaInfo.LocalImage(url)))
        navigator.navigate(route)
    }

    override fun gotoTitleCompose() {
        navigator.navigate(TitleComposeScreen)
    }

    override fun gotoCommunityCompose() {
        navigator.navigate(CommunityComposeScreen)
    }

    override fun gotoRoomCompose() {
        navigator.navigate(RoomComposeScreen)
    }

    override fun gotoSettingPage(objectId: PrimaryKey, objectType: ObjectType) {
        if (objectType == ObjectType.COMMUNITY) {
            navigator.navigate(CommunitySettingScreen(objectId))
        } else {
            navigator.navigate(RoomSettingScreen(objectId))
        }
    }

    override fun gotoReactionListPage(topicId: PrimaryKey) {
        navigator.navigate(ReactionListScreen(topicId))
    }
}

@OptIn(ExperimentalResourceApi::class)
fun NavGraphBuilder.buildRootNav(
    navigator: NavHostController,
) {
    buildMainScreen()
    composable<AboutScreen> {
        val libraries by rememberLibraries {
            Res.readBytes("files/aboutlibraries.json").decodeToString()
        }
        Surface {
            LibrariesContainer(
                libraries,
                Modifier.fillMaxSize().statusBarsPadding(),
                colors = LibraryDefaults.libraryColors()
            )
        }
    }
    composable<MediaScreen> {
        val route = it.toRoute<MediaScreen>()
        val pack = commonJson.decodeFromString<MultiMediaInfo>(route.json)
        MediaPage(pack)
    }
    buildComposeScreen(navigator)
}

private fun NavGraphBuilder.buildMainScreen() {
    composable<HomeScreen> {
        HomePage()
    }
    composable<LoginScreen> {
        LoginPage()
    }
    composable<CommunityScreen> {
        val screen = it.toRoute<CommunityScreen>()
        CommunityPage(
            screen.communityId,
            screen.showDialog
        )
    }
    composable<RoomScreen> {
        val screen = it.toRoute<RoomScreen>()
        RoomPage(screen.roomId, screen.showDialog)
    }
    composable<TopicScreen> {
        TopicPage(it.toRoute<TopicScreen>().topicId)
    }

    composable<MemberScreen> {
        val (objectType, objectId) = it.toRoute<MemberScreen>()
        MemberPage(
            objectId,
            ObjectType.valueOf(objectType)
        )
    }
    composable<UserScreen> {
        val route = it.toRoute<UserScreen>()
        UserPage(route.uid)
    }
    composable<UserSettingScreen> {
        UserSettingPage()
    }
    composable<PreferenceScreen> {
        PreferencePage()
    }
    composable<CommunitySettingScreen> {
        val communityId = it.toRoute<CommunitySettingScreen>().communityId
        CommunitySettingPage(communityId)
    }
    composable<RoomSettingScreen> {
        val roomId = it.toRoute<RoomSettingScreen>().roomId
        RoomSettingPage(roomId)
    }
    composable<ReactionListScreen> {
        val topicId = it.toRoute<ReactionListScreen>().topicId
        ReactionListPage(topicId)
    }
}

private fun NavGraphBuilder.buildComposeScreen(navigator: NavHostController) {
    composable<TitleComposeScreen> {
        TitleComposePage()
    }
    composable<CommunityComposeScreen> {
        CommunityComposePage()
    }
    composable<RoomComposeScreen> {
        RoomComposePage()
    }
    composable<TopicComposeScreen> {
        val (objectType, objectId, enableExperimental, privateRoomId, communityId) = it.toRoute<TopicComposeScreen>()
        TopicComposePage(
            ObjectType.valueOf(
                objectType
            ),
            objectId,
            enableExperimental,
            privateRoomId,
            communityId
        ) {
            navigator.popBackStack()
        }
    }
}
