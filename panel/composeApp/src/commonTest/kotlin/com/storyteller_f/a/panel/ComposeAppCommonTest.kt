package com.storyteller_f.a.panel

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.storyteller_f.a.panel.common.PanelAllCommunitiesScreen
import com.storyteller_f.a.panel.common.PanelAllFilesScreen
import com.storyteller_f.a.panel.common.PanelAllPrivateRoomsScreen
import com.storyteller_f.a.panel.common.PanelAllPublicRoomsScreen
import com.storyteller_f.a.panel.common.PanelAllTitlesScreen
import com.storyteller_f.a.panel.common.PanelAllTopicsScreen
import com.storyteller_f.a.panel.common.PanelAllUsersScreen
import com.storyteller_f.a.panel.common.PanelCommunityDetailScreen
import com.storyteller_f.a.panel.common.PanelFileDetailScreen
import com.storyteller_f.a.panel.common.PanelRoomDetailScreen
import com.storyteller_f.a.panel.common.PanelTitleDetailScreen
import com.storyteller_f.a.panel.common.PanelTopicDetailScreen
import com.storyteller_f.a.panel.common.PanelUserDetailScreen
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeAppCommonTest {
    @Test
    fun listAndDetailRoutesUseMatchingScenes() {
        assertListDetailPair(
            PanelAllUsersScreen,
            PanelUserDetailScreen(1),
            PanelListDetailScene.Users,
        )
        assertListDetailPair(
            PanelAllCommunitiesScreen,
            PanelCommunityDetailScreen(1),
            PanelListDetailScene.Communities,
        )
        assertListDetailPair(
            PanelAllPublicRoomsScreen,
            PanelRoomDetailScreen(1),
            PanelListDetailScene.Rooms,
        )
        assertListDetailPair(
            PanelAllPrivateRoomsScreen,
            PanelRoomDetailScreen(1),
            PanelListDetailScene.Rooms,
        )
        assertListDetailPair(
            PanelAllTopicsScreen,
            PanelTopicDetailScreen(1),
            PanelListDetailScene.Topics,
        )
        assertListDetailPair(
            PanelAllFilesScreen,
            PanelFileDetailScreen(1),
            PanelListDetailScene.Files,
        )
        assertListDetailPair(
            PanelAllTitlesScreen,
            PanelTitleDetailScreen(1),
            PanelListDetailScene.Titles,
        )
    }

    @Test
    fun selectingSameSceneDetailReplacesCurrentDetail() {
        val backStack = NavBackStack<NavKey>(PanelAllUsersScreen, PanelUserDetailScreen(1))

        backStack.addPanelDetail(PanelUserDetailScreen(2))

        assertEquals(
            listOf(PanelAllUsersScreen, PanelUserDetailScreen(2)),
            backStack,
        )
    }

    @Test
    fun navigatingToAnotherDetailScenePreservesHistory() {
        val backStack = NavBackStack<NavKey>(PanelAllUsersScreen, PanelUserDetailScreen(1))

        backStack.addPanelDetail(PanelCommunityDetailScreen(2))

        assertEquals(
            listOf(
                PanelAllUsersScreen,
                PanelUserDetailScreen(1),
                PanelCommunityDetailScreen(2),
            ),
            backStack,
        )
    }
}

private fun assertListDetailPair(list: NavKey, detail: NavKey, scene: PanelListDetailScene) {
    assertEquals(
        PanelListDetailDestination(scene, PanelListDetailPane.List),
        list.panelListDetailDestination(),
    )
    assertEquals(
        PanelListDetailDestination(scene, PanelListDetailPane.Detail),
        detail.panelListDetailDestination(),
    )
}
