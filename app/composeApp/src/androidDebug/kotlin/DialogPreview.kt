import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.storyteller_f.a.app.compose_app.compontents.CustomAlertDialogInternal
import com.storyteller_f.a.app.compose_app.compontents.CustomAlertDialogState
import com.storyteller_f.a.app.compose_app.compontents.GlobalDialogInternal
import com.storyteller_f.a.app.compose_app.compontents.GlobalDialogState
import kotlinx.collections.immutable.persistentListOf

@Preview(showSystemUi = true)
@Composable
private fun PreviewLoading() {
    GlobalDialogInternal(GlobalDialogState.Loading()) {
    }
}

@Preview(showSystemUi = true)
@Composable
private fun PreviewError() {
    GlobalDialogInternal(GlobalDialogState.Error(Exception("Error 404"))) {
    }
}

@Preview
@Composable
private fun PreviewCustomDialog() {
    CustomAlertDialogInternal({
    }, CustomAlertDialogState(null, "test")) {
    }
}
