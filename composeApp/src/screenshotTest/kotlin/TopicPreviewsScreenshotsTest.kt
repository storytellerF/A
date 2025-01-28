import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.storyteller_f.a.app.compontents.TopicContentField
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo

class TopicPreviewsScreenshotsTest {

    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        TopicContentField(TopicInfo.EMPTY.copy(content = TopicContent.Plain("hello")))
    }
}