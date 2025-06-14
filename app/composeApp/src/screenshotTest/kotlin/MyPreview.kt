import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.storyteller_f.a.app.pages.user.UserDialogInternal
import com.storyteller_f.shared.model.UserInfo

private class UserInfoPreviewProvider : PreviewParameterProvider<UserInfo> {
    override val values: Sequence<UserInfo>
        get() = sequence {
            yield(UserInfo.EMPTY)
        }
}

@Preview
@Composable
private fun PreviewMyPage(@PreviewParameter(UserInfoPreviewProvider::class) userInfo: UserInfo) {
//    UserDialogInternal(userInfo = userInfo, {
//    }) {
//    }
}
