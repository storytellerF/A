package com.storyteller_f.a.app.pages.community

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.LocalClient
import com.storyteller_f.a.app.LocalToaster
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.compontents.CommunityIcon
import com.storyteller_f.a.app.compontents.CommunityPoster
import com.storyteller_f.a.app.compontents.SettingOptionResettableView
import com.storyteller_f.a.app.compontents.SettingOptionView
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.model.OnCommunityUpdated
import com.storyteller_f.a.app.model.createCommunityViewModel
import com.storyteller_f.a.app.pages.user.ObjectSettingDialog
import com.storyteller_f.a.app.pages.user.SettingOption
import com.storyteller_f.a.client_lib.updateCommunityInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.client.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitySettingPage(communityId: PrimaryKey) {
    var currentOption by remember {
        mutableStateOf<SettingOption?>(null)
    }
    val communityViewModel = createCommunityViewModel(communityId)
    val communityInfo by communityViewModel.handler.data.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    val closeDialog = {
        currentOption = null
    }
    Scaffold {
        communityInfo?.let { it1 ->
            CommunitySettingInternal(it, { opt ->
                currentOption = opt
            }, it1)
        }
        val client = LocalClient.current
        val scope = rememberCoroutineScope()
        ObjectSettingDialog(closeDialog, currentOption, sheetState, { media ->
            scope.launch {
                globalDialogState.use {
                    val body = if (currentOption is SettingOption.Poster) {
                        UpdateCommunityBody(poster = media.name)
                    } else {
                        UpdateCommunityBody(icon = media.name)
                    }
                    val newInfo = client.updateCommunityInfo(communityId, body).getOrThrow()
                    bus.emit(OnCommunityUpdated(newInfo))
                    closeDialog()
                }
            }
        }, { input ->
            scope.launch {
                updateCommunity(currentOption, client, input, closeDialog, communityId)
            }
        })
    }
}

@Composable
private fun CommunitySettingInternal(
    values: PaddingValues,
    showDialog: (SettingOption) -> Unit,
    communityInfo: CommunityInfo,
) {
    val toasterState = LocalToaster.current
    val client = LocalClient.current
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(values)) {
        SettingOptionResettableView("Icon", communityInfo.icon != null, {
            if (it) {
                scope.launch {
                    globalDialogState.use {
                        val body = UpdateCommunityBody(icon = "")
                        val newInfo = client.updateCommunityInfo(communityInfo.id, body).getOrThrow()
                        bus.emit(OnCommunityUpdated(newInfo))
                    }
                }
            } else
                showDialog(SettingOption.Icon(communityInfo.icon?.name))
        }, {
            CommunityIcon(communityInfo, showDialog = false, setClickEvent = false) {}
        })
        SettingOptionResettableView("Poster", communityInfo.hasPoster, {
            if (it) {
                scope.launch {
                    globalDialogState.use {
                        val body = UpdateCommunityBody(poster = "")
                        val newInfo = client.updateCommunityInfo(communityInfo.id, body).getOrThrow()
                        bus.emit(OnCommunityUpdated(newInfo))
                    }
                }
            } else
                showDialog(SettingOption.Poster(communityInfo.poster?.name))
        }, {
            Box(modifier = Modifier.width(100.dp).aspectRatio(3 / 4f)) {
                CommunityPoster(communityInfo)
            }
        })
        SettingOptionView("Name", {
            showDialog(SettingOption.Name(communityInfo.name))
        }, {
            Text(communityInfo.name, textDecoration = TextDecoration.Underline)
        })
        val aid = communityInfo.aid
        SettingOptionView("Aid", {
            toasterState.show("forbid", duration = 1.seconds)
        }, {
            Text(aid)
        })
    }
}

private suspend fun updateCommunity(
    showInputDialog: SettingOption?,
    client: HttpClient,
    string: String,
    closeDialog: () -> Unit,
    communityId: PrimaryKey
) {
    val body = when (showInputDialog) {
        is SettingOption.Name -> {
            UpdateCommunityBody(name = string)
        }

        else -> {
            null
        }
    } ?: return
    globalDialogState.use {
        val newInfo = client.updateCommunityInfo(communityId, body).getOrThrow()
        bus.emit(OnCommunityUpdated(newInfo))
        closeDialog()
    }
}
