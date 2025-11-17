package com.storyteller_f.a.panel.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storyteller_f.a.client.core.PanelSessionManager
import com.storyteller_f.a.panel.panelAccountInstance
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.storage.ModelStorage
import io.github.aakira.napier.Napier

@Composable
fun createPanelAllUserViewModel() = panelViewModel { sessionManager, modelStorage ->
    AllUsersViewModel(sessionManager, modelStorage)
}

@Composable
fun createPanelOverviewViewModel() = panelViewModel { sessionManager, modelStorage ->
    OverviewViewModel(sessionManager, modelStorage)
}

@Composable
fun createPanelAllCommunitiesViewModel() = panelViewModel { sessionManager, modelStorage ->
    AllCommunitiesViewModel(sessionManager, modelStorage)
}

@Composable
fun createPanelAllPublicRoomsViewModel() = panelViewModel { sessionManager, modelStorage ->
    AllPublicRoomsViewModel(sessionManager, modelStorage)
}

@Composable
fun createPanelAllPrivateRoomsViewModel() = panelViewModel { sessionManager, modelStorage ->
    AllPrivateRoomsViewModel(sessionManager, modelStorage)
}

@Composable
fun createPanelAllTopicsViewModel() = panelViewModel { sessionManager, modelStorage ->
    AllTopicsViewModel(sessionManager, modelStorage)
}

@Composable
fun createPanelAllTitlesViewModel() = panelViewModel { sessionManager, modelStorage ->
    AllTitlesViewModel(sessionManager, modelStorage)
}

@Composable
fun createPanelAllFilesViewModel() = panelViewModel { sessionManager, modelStorage ->
    AllFilesViewModel(sessionManager, modelStorage)
}

@Composable
inline fun <reified VM : ViewModel> panelViewModel(
    keys: List<Comparable<*>?>? = null,
    crossinline factory: (PanelSessionManager, ModelStorage) -> VM
): VM {
    val sessionManager = panelAccountInstance.sessionManager
    val modelStorage by panelAccountInstance.database.collectAsState()
    SideEffect {
        Napier.i {
            "viewModel ${VM::class.simpleName}$keys composable"
        }
    }
    return viewModel(key = "${keys?.joinToString()}") {
        Napier.i {
            "viewModel ${VM::class.simpleName}$keys build"
        }
        factory(sessionManager, modelStorage)
    }
}

@Composable
fun createPanelUserViewModel(
    id: PrimaryKey
) = panelViewModel(keys = listOf("user", id)) { sessionManager, modelStorage ->
    IdUserViewModel(sessionManager, modelStorage, id)
}

@Composable
fun createPanelJoinedCommunitiesViewModel(
    uid: PrimaryKey
) = panelViewModel(keys = listOf("user-communities", uid)) { sessionManager, modelStorage ->
    UserJoinedCommunitiesViewModel(sessionManager, modelStorage, uid)
}

@Composable
fun createPanelJoinedRoomsViewModel(
    uid: PrimaryKey
) = panelViewModel(keys = listOf("user-rooms", uid)) { sessionManager, modelStorage ->
    UserJoinedRoomsViewModel(sessionManager, modelStorage, uid)
}

@Composable
fun createPanelReceivedTitlesViewModel(
    uid: PrimaryKey
) = panelViewModel(keys = listOf("user-titles", uid)) { sessionManager, modelStorage ->
    UserReceivedTitlesViewModel(sessionManager, modelStorage, uid)
}

@Composable
fun createPanelUserFilesViewModel(
    uid: PrimaryKey
) = panelViewModel(keys = listOf("user-files", uid)) { sessionManager, modelStorage ->
    UserFilesViewModel(sessionManager, modelStorage, uid)
}

@Composable
fun createPanelUserLogsViewModel(
    uid: PrimaryKey
) = panelViewModel(keys = listOf("user-logs", uid)) { sessionManager, _ ->
    UserLogsViewModel(sessionManager, uid)
}

@Composable
fun createPanelCommunityViewModel(
    id: PrimaryKey
) = panelViewModel(keys = listOf("community", id)) { sessionManager, modelStorage ->
    IdCommunityViewModel(sessionManager, modelStorage, id)
}

@Composable
fun createPanelRoomViewModel(
    id: PrimaryKey
) = panelViewModel(keys = listOf("room", id)) { sessionManager, modelStorage ->
    IdRoomViewModel(sessionManager, modelStorage, id)
}

@Composable
fun createPanelTopicViewModel(
    id: PrimaryKey
) = panelViewModel(keys = listOf("topic", id)) { sessionManager, modelStorage ->
    IdTopicViewModel(sessionManager, modelStorage, id)
}

@Composable
fun createPanelFileViewModel(
    id: PrimaryKey
) = panelViewModel(keys = listOf("file", id)) { sessionManager, modelStorage ->
    IdFileViewModel(sessionManager, modelStorage, id)
}

@Composable
fun createPanelTitleViewModel(
    id: PrimaryKey
) = panelViewModel(keys = listOf("title", id)) { sessionManager, modelStorage ->
    IdTitleViewModel(sessionManager, modelStorage, id)
}
