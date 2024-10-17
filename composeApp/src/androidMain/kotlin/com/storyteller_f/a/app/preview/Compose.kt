package com.storyteller_f.a.app.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.storyteller_f.a.app.topic.EditTopicPage
import com.storyteller_f.a.app.topic.PreviewTopicPage
import com.storyteller_f.shared.obj.AddTaskValue
import kotlinx.serialization.json.Json
import java.io.File

private class ContentListProvider : PreviewParameterProvider<String> {
    override val values: Sequence<String>
        get() = sequence {
            val f = File(com.storyteller_f.a.app.BuildKonfig.PROJECT_PATH, "../../AData/data/preset_topic.json")
            if (f.exists()) {
                val value = Json.decodeFromString<AddTaskValue>(f.readText())
                yieldAll(value.topicData.orEmpty().filter {
                    it.room != null
                }.map {
                    it.content
                })
            }
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
    PreviewTopicPage(content)
}
