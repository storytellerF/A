package com.storyteller_f.a.app.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.storyteller_f.a.app.BuildKonfig
import com.storyteller_f.a.app.user.UserDialogInternal
import com.storyteller_f.shared.obj.AddTaskValue
import com.storyteller_f.shared.model.UserInfo
import kotlinx.serialization.json.Json
import java.io.File

private class UserInfoPreviewProvider : PreviewParameterProvider<UserInfo> {
    override val values: Sequence<UserInfo>
        get() = sequence {
            val f = File(BuildKonfig.PROJECT_PATH, "../../AData/data/preset_user.json")
            if (f.exists()) {
                val value = Json.decodeFromString<AddTaskValue>(f.readText())
                yieldAll(value.userData.orEmpty().map {
                    UserInfo(0u, "", 0, it.id, it.name, null)
                })
            }
        }

}

@Preview
@Composable
private fun PreviewMyPage(@PreviewParameter(UserInfoPreviewProvider::class) userInfo: UserInfo) {
    UserDialogInternal(userInfo = userInfo)
}
