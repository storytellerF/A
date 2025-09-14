import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.storyteller_f.a.app.compose_app.TopicComposeData
import com.storyteller_f.a.app.compose_app.compontents.InteractionRowInternal
import com.storyteller_f.a.app.compose_app.compontents.TopicContentField
import com.storyteller_f.a.app.compose_app.pages.topic.EditTopicPage
import com.storyteller_f.a.app.compose_app.pages.topic.PreviewTopicPage
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType

private class ContentListProvider : PreviewParameterProvider<String> {
    override val values: Sequence<String>
        get() = sequence {
            yield("hello world")
        }
}

@Preview(showSystemUi = true)
@Composable
private fun PreviewTopicEdit(@PreviewParameter(ContentListProvider::class) content: String) {
    EditTopicPage(content, TopicComposeData.User(0, 0L ob ObjectType.USER)) {
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
