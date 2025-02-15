package com.storyteller_f.a.app.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.storyteller_f.a.app.AppConfig
import com.storyteller_f.a.app.pages.user.UserDialogInternal
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.PresetValue
import kotlinx.serialization.json.Json
import java.io.File

private class UserInfoPreviewProvider : PreviewParameterProvider<UserInfo> {
    override val values: Sequence<UserInfo>
        get() = sequence {
            val f = File(AppConfig.PROJECT_PATH, "../../AData/data/preset_user.json")
            if (f.exists()) {
                val value = Json.decodeFromString<PresetValue>(f.readText())
                yieldAll(value.userData.orEmpty().map {
                    UserInfo.EMPTY.copy(aid = it.id, nickname = it.name)
                })
            }
        }
}

@Preview
@Composable
private fun PreviewMyPage(@PreviewParameter(UserInfoPreviewProvider::class) userInfo: UserInfo) {
    UserDialogInternal(userInfo = userInfo, {
    }) {
    }
}
