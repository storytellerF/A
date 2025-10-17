package com.storyteller_f.a.app.compose_app.pages.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.compose_app.LocalAppNav
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.common.OnCommunityCreated
import com.storyteller_f.a.app.compose_app.pages.title.CommonComposePage
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.shared.obj.NewCommunity
import kotlinx.coroutines.launch

@Composable
fun CommunityComposePage() {
    var name by remember {
        mutableStateOf("")
    }
    var aid by remember {
        mutableStateOf("")
    }
    val sessionManager = LocalSessionManager.current
    val appNav = LocalAppNav.current
    val scope = rememberCoroutineScope()
    val globalDialogController = LocalGlobalDialog.current
    CommonComposePage({
        scope.launch {
            globalDialogController.useResult {
                sessionManager.createCommunity(NewCommunity(name, aid))
            }.onSuccess {
                globalDialogController.emitEvent(
                    OnCommunityCreated(
                        it
                    )
                )
                appNav.back()
            }
        }
    }) {
        Column(
            modifier = Modifier.width(300.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedTextField(name, onValueChange = {
                name = it
            }, label = {
                Text("name")
            })
            OutlinedTextField(aid, onValueChange = {
                aid = it
            }, label = {
                Text("aid")
            })
        }
    }
}
