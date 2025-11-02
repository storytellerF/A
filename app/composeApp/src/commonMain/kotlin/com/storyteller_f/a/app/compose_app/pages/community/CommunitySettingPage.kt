package com.storyteller_f.a.app.compose_app.pages.community

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.LocalToaster
import com.storyteller_f.a.app.compose_app.common.OnCommunityUpdated
import com.storyteller_f.a.app.compose_app.common.createCommunityViewModel
import com.storyteller_f.a.app.compose_app.components.CommunityPoster
import com.storyteller_f.a.app.compose_app.components.SettingOptionResettableView
import com.storyteller_f.a.app.compose_app.components.SettingOptionView
import com.storyteller_f.a.app.compose_app.pages.user.ObjectSettingDialog
import com.storyteller_f.a.app.compose_app.pages.user.SettingOption
import com.storyteller_f.a.app.core.compontents.GlobalDialogController
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.updateCommunityInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitySettingPage(communityId: PrimaryKey) {
    var currentOption by remember {
        mutableStateOf<SettingOption?>(null)
    }
    val communityViewModel =
        createCommunityViewModel(communityId)
    val communityInfo by communityViewModel.handler.data.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    val globalDialogController = LocalGlobalDialog.current
    val closeDialog = {
        currentOption = null
    }
    Scaffold {
        communityInfo?.let { it1 ->
            CommunitySettingInternal(it, { opt ->
                currentOption = opt
            }, it1)
        }
        val sessionManager = LocalSessionManager.current
        val scope = rememberCoroutineScope()
        ObjectSettingDialog(
            closeDialog,
            currentOption,
            sheetState,
            { media ->
                scope.launch {
                    globalDialogController.useResult {
                        val body =
                            if (currentOption is SettingOption.Poster) {
                                UpdateCommunityBody(poster = media.id)
                            } else {
                                UpdateCommunityBody(icon = media.id)
                            }
                        sessionManager.updateCommunityInfo(communityId, body)
                    }.onSuccess { newInfo ->
                        globalDialogController.emitEvent(OnCommunityUpdated(newInfo))
                        closeDialog()
                    }
                }
            },
            { input ->
                scope.launch {
                    updateCommunity(
                        communityId,
                        sessionManager,
                        input,
                        currentOption,
                        globalDialogController,
                        closeDialog
                    )
                }
            }
        )
    }
}

@Composable
private fun CommunitySettingInternal(
    values: PaddingValues,
    showDialog: (SettingOption) -> Unit,
    communityInfo: CommunityInfo,
) {
    val toasterState = LocalToaster.current
    val sessionManager = LocalSessionManager.current
    val scope = rememberCoroutineScope()
    val globalDialogController = LocalGlobalDialog.current
    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(values)) {
        SettingOptionResettableView(
            "Icon",
            communityInfo.icon != null,
            {
                if (it) {
                    scope.launch {
                        updateCommunityInfo(globalDialogController, sessionManager, communityInfo)
                    }
                } else {
                    showDialog(SettingOption.Icon(communityInfo.icon?.fullName))
                }
            },
            {
                CommunityIconWithDialog(
                    communityInfo,
                    showDialog = false,
                    setClickEvent = false
                ) {}
            }
        )
        val globalDialogController = LocalGlobalDialog.current
        SettingOptionResettableView(
            "Poster",
            communityInfo.hasPoster,
            {
                if (it) {
                    scope.launch {
                        updateCommunityInfo(globalDialogController, sessionManager, communityInfo)
                    }
                } else {
                    showDialog(SettingOption.Poster(communityInfo.poster?.fullName))
                }
            },
            {
                Box(modifier = Modifier.width(100.dp).aspectRatio(3 / 4f)) {
                    CommunityPoster(communityInfo)
                }
            }
        )
        SettingOptionView("Name", {
            showDialog(SettingOption.Name(communityInfo.name))
        }, {
            Text(communityInfo.name, textDecoration = TextDecoration.Underline)
        })
        val aid = communityInfo.aid
        SettingOptionView("Aid", {
            toasterState.showMessage("forbid")
        }, {
            Text(aid)
        })
    }
}

private suspend fun updateCommunityInfo(
    globalDialogController: GlobalDialogController,
    sessionManager: UserSessionManager,
    communityInfo: CommunityInfo,
) {
    globalDialogController.useResult {
        val body = UpdateCommunityBody(icon = 0)
        sessionManager.updateCommunityInfo(communityInfo.id, body)
    }.onSuccess { newInfo ->
        globalDialogController.emitEvent(OnCommunityUpdated(newInfo))
    }
}

private suspend fun updateCommunity(
    communityId: PrimaryKey,
    client: UserSessionManager,
    string: String,
    showInputDialog: SettingOption?,
    globalDialogController: GlobalDialogController,
    closeDialog: () -> Unit,
) {
    val body = when (showInputDialog) {
        is SettingOption.Name -> {
            UpdateCommunityBody(name = string)
        }

        else -> {
            null
        }
    } ?: return
    globalDialogController.useResult {
        client.updateCommunityInfo(communityId, body)
    }.onSuccess { newInfo ->
        globalDialogController.emitEvent(OnCommunityUpdated(newInfo))
        closeDialog()
    }
}
