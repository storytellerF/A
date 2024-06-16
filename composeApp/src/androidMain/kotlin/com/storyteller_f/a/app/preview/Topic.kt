package com.storyteller_f.a.app.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.compontents.ReactionRow
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.app.topic.TopicCellInternal
import com.storyteller_f.a.app.topic.TopicContentField
import com.storyteller_f.a.app.topic.TopicDialogInternal
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.AddTaskValue
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.json.Json

private class TopicPagePreviewProvider : PreviewParameterProvider<Pair<TopicInfo, List<TopicInfo>>> {
    override val values: Sequence<Pair<TopicInfo, List<TopicInfo>>>
        get() = sequence {
            val p = Path(
                com.storyteller_f.a.app.BuildKonfig.PROJECT_PATH,
                "../../AData/data/preset_topic.json"
            )
            if (SystemFileSystem.exists(p)) {
                val content = SystemFileSystem.source(p).buffered().readString()
                val value = Json.decodeFromString<AddTaskValue>(content)
                val value1 = value.topicData.orEmpty().filter {
                    it.room != null
                }.map {
                    TopicInfo.EMPTY.copy(content = TopicContent.Plain(it.content))
                }
                yield(value1.first() to value1.subList(1, value1.size))
            }
        }
}

@Preview
@Composable
fun PreviewTopic(@PreviewParameter(TopicPagePreviewProvider::class) param: Pair<TopicInfo, List<TopicInfo>>) {
    val topic = param.first
    Column {

        CustomSearchBar {
            Icon(Icons.Default.Topic, "topic", modifier = Modifier.clickable {
            })
        }

        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TopicContentField(topic.content)
            }

            item {
                ReactionRow()
            }

            item {
                HorizontalDivider()
            }

            items(param.second) {
                TopicCellInternal(it, true, UserInfo.EMPTY, true)
            }
        }
    }
}

@Preview
@Composable
fun PreviewTopicDialog() {
    TopicDialogInternal(TopicInfo.EMPTY, UserInfo.EMPTY)
}
