package com.storyteller_f.a.app.pages.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.storyteller_f.a.app.LocalAppNavFactory
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.LocalUserInfo
import com.storyteller_f.a.app.common.OnTitleCreated
import com.storyteller_f.a.app.common.createTitleComposeViewModel
import com.storyteller_f.a.app.core.components.emitEvent
import com.storyteller_f.a.app.core.components.request
import com.storyteller_f.a.client.core.createTitle
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.a.app.AppGlobalDialogController
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.a.api.NewTitle
import kotlinx.coroutines.launch

@Composable
fun RoomTitleComposePage(roomId: PrimaryKey) {
    LocalUserInfo.current ?: return
    RoomTitleComposeInternal(roomId)
}

@Composable
private fun RoomTitleComposeInternal(roomId: PrimaryKey) {
    val scope = roomId ob ObjectType.ROOM
    val vm = createTitleComposeViewModel(
        initialScope = scope,
        initialType = com.storyteller_f.shared.model.TitleType.JOIN,
        lockScope = true,
        lockType = true
    )
    val appNavFactory = LocalAppNavFactory.current
    val coroutineScope = rememberCoroutineScope()
    val globalDialog = LocalGlobalDialog.current

    CommonComposePage({
        coroutineScope.launch {
            globalDialog.useResult {
                createTitle(vm.buildNewTitle().getOrThrow())
            }.onSuccess {
                appNavFactory.newAppNav().back()
            }
        }
    }) {
        TitleComposeInternalEdit(vm)
    }
    TitleComposeSheet(vm)
}

private suspend fun AppGlobalDialogController.createTitle(
    newTitle: NewTitle,
): Result<TitleInfo> {
    return request { createTitle(newTitle) }.onSuccess { title ->
        emitEvent(OnTitleCreated(title))
    }
}
