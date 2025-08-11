import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.dokar.sonner.rememberToasterState
import com.storyteller_f.a.app.compose_app.AppNav
import com.storyteller_f.a.app.compose_app.LocalAppNav
import com.storyteller_f.a.app.compose_app.LocalMainSessionManager
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.LocalToaster
import com.storyteller_f.a.app.compose_app.Sonner
import com.storyteller_f.a.app.compose_app.compontents.InteractionRowInternal
import com.storyteller_f.a.app.compose_app.compontents.TopicContentField
import com.storyteller_f.a.app.compose_app.pages.topic.EditTopicPage
import com.storyteller_f.a.app.compose_app.pages.topic.PreviewTopicPage
import com.storyteller_f.a.app.compose_app.pages.topic.TopicDialogInternal
import com.storyteller_f.a.client.core.SessionManager
import com.storyteller_f.a.client.core.SessionModel
import com.storyteller_f.a.client.core.WebSocketClient
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.StateFlow

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
    PreviewTopicPage(content, ObjectTuple(0, ObjectType.USER))
}

@Preview(showBackground = true)
@Composable
fun TopicContentPreview() {
    TopicContentField(TopicInfo.Companion.EMPTY.copy(content = TopicContent.Plain("hello world")))
}

@Preview
@Composable
fun ReactionPreview() {
    InteractionRowInternal(
        emptyList(),
        TopicInfo.Companion.EMPTY,
        {},
        {}
    )
}
