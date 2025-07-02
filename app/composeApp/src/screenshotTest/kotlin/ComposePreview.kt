import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.storyteller_f.a.app.compose_app.pages.topic.EditTopicPage
import com.storyteller_f.a.app.compose_app.pages.topic.PreviewTopicPage

private class ContentListProvider : PreviewParameterProvider<String> {
    override val values: Sequence<String>
        get() = sequence {
            yield("hello world")
        }
}

@Preview(showSystemUi = true)
@Composable
private fun PreviewTopicEdit(@PreviewParameter(ContentListProvider::class) content: String) {
    EditTopicPage(content) {
    }
}

@Preview(showSystemUi = true)
@Composable
private fun PreviewTopicPreview(@PreviewParameter(ContentListProvider::class) content: String) {
    PreviewTopicPage(content, emptyList())
}
