import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.storyteller_f.a.app.compontents.TopicContentField
import com.storyteller_f.a.app.pages.topic.TopicDialogInternal
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo

private class TopicPagePreviewProvider : PreviewParameterProvider<Pair<TopicInfo, List<TopicInfo>>> {
    override val values: Sequence<Pair<TopicInfo, List<TopicInfo>>>
        get() = sequence {
            yield(TopicInfo.EMPTY to listOf(TopicInfo.EMPTY))
        }
}

@Preview
@Composable
fun PreviewTopic(@PreviewParameter(TopicPagePreviewProvider::class) param: Pair<TopicInfo, List<TopicInfo>>) {

}

@Preview
@Composable
fun PreviewTopicDialog() {
    TopicDialogInternal(TopicInfo.EMPTY, UserInfo.EMPTY) {}
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TopicContentField(TopicInfo.EMPTY.copy(content = TopicContent.Plain("hello world")))
}