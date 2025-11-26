package com.storyteller_f.a.app.pages.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.LocalAppNavFactory
import com.storyteller_f.a.app.common.UserViewModel
import com.storyteller_f.a.app.common.createUserViewModel
import com.storyteller_f.a.app.core.components.RefCellStateView
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey

@Composable
fun UserRefCell(userId: PrimaryKey, onClick: ((UserInfo) -> Unit)? = null) {
    val appNavFactory = LocalAppNavFactory.current
    val viewModel = createUserViewModel(userId)
    UserRefCellInternal(viewModel) {
        onClick?.invoke(it) ?: appNavFactory.newAppNav().gotoUser(it.id)
    }
}

@Composable
fun UserRefCell(userAid: String, onClick: ((UserInfo) -> Unit)? = null) {
    val appNavFactory = LocalAppNavFactory.current
    val viewModel = createUserViewModel(userAid)
    UserRefCellInternal(viewModel) {
        onClick?.invoke(it) ?: appNavFactory.newAppNav().gotoUser(it.id)
    }
}

@Composable
private fun UserRefCellInternal(
    viewModel: UserViewModel,
    onClick: ((UserInfo) -> Unit)? = null
) {
    val userInfo by viewModel.handler.data.collectAsState()
    val shape = RoundedCornerShape(10.dp)
    val appNavFactory = LocalAppNavFactory.current
    RefCellStateView(
        viewModel.handler,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, shape)
            .clip(shape)
            .clickable {
                userInfo?.let {
                    onClick ?: appNavFactory.newAppNav().gotoUser(it.id)
                }
            }
    ) { info ->
        UnboundedUserCell(info)
    }
}
