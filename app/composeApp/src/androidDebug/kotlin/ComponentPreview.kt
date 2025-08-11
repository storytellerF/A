import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.storyteller_f.a.app.compose_app.compontents.ButtonNav
import com.storyteller_f.a.app.compose_app.compontents.IconRes
import com.storyteller_f.a.app.compose_app.ui.MaterialSymbolsOutlined

@Preview
@Composable
private fun PreviewButtonNav() {
    ButtonNav(MaterialSymbolsOutlined.Refresh, "refresh") {

    }
}
