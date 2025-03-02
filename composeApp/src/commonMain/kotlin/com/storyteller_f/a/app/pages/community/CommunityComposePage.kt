package com.storyteller_f.a.app.pages.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.LocalClient
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.model.OnCommunityCreated
import com.storyteller_f.a.app.pages.title.CommonComposePage
import com.storyteller_f.a.client_lib.createCommunity
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
    val client = LocalClient.current
    val appNav = LocalAppNav.current
    val scope = rememberCoroutineScope()
    CommonComposePage({
        scope.launch {
            if (globalDialogState.use {
                    val community = client.createCommunity(NewCommunity(name, aid)).getOrThrow()
                    bus.emit(OnCommunityCreated(community))
                }.isSuccess) {
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
