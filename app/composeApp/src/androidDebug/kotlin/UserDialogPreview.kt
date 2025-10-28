import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.storyteller_f.a.app.compose_app.ProvideFontIcon
import com.storyteller_f.a.app.compose_app.pages.user.UserDialog
import com.storyteller_f.shared.model.UserInfo
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@PreviewLightDark
@Composable
fun UserDialogPreview() {
    PreviewContextConfigurationEffect()
    ProvideFontIcon {
        UserDialog(true, UserInfo.EMPTY, true, {}) { }
    }
}