package com.storyteller_f.a.app.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.client
import com.storyteller_f.a.client_lib.getUserInfo
import com.storyteller_f.a.app.common.SimpleViewModel
import com.storyteller_f.a.app.common.serviceCatching
import com.storyteller_f.a.app.topic.UserHeadRow
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.OKey
import moe.tlaster.precompose.viewmodel.viewModel


@Composable
fun UserRefCell(modifier: Modifier = Modifier, userId: OKey) {
    val viewModel = viewModel(UserViewModel::class, keys = listOf("user", userId)) {
        UserViewModel(userId)
    }
    val data by viewModel.handler.data.collectAsState()

    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp)).padding(10.dp)) {
        UserHeadRow(data)
    }
}

class UserViewModel(private val userId: OKey) : SimpleViewModel<UserInfo>() {
    init {
        load()
    }

    override suspend fun loadInternal() {
        handler.request {
            serviceCatching {
                client.getUserInfo(userId)
            }
        }
    }


}
