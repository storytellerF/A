package com.storyteller_f.a.app.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.compontents.TopicCell
import com.storyteller_f.a.app.pages.room.InputGroupInternal
import com.storyteller_f.a.app.pages.room.RoomCell
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.PresetValue
import com.storyteller_f.shared.type.ObjectType
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.json.Json

private class MessageListProvider : PreviewParameterProvider<List<TopicInfo>> {
    override val values: Sequence<List<TopicInfo>>
        get() = sequence {
            val p = Path(
                com.storyteller_f.a.app.BuildKonfig.PROJECT_PATH,
                "../../AData/data/preset_topic.json"
            )
            if (SystemFileSystem.exists(p)) {
                val content = SystemFileSystem.source(p).buffered().readString()
                val value = Json.decodeFromString<PresetValue>(content)
                yield(value.topicData.orEmpty().filter {
                    it.room != null
                }.map {
                    TopicInfo.EMPTY.copy(content = TopicContent.Plain(it.content))
                })
            }
        }
}

@Composable
@Preview
private fun PreviewRoom(@PreviewParameter(MessageListProvider::class) topicInfos: List<TopicInfo>) {
    Column {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(topicInfos) {
                TopicCell(it, false)
            }
        }
        InputGroupInternal(
            0,
            ObjectType.ROOM,
            "test",
            MaterialTheme.colorScheme.tertiaryContainer,
            null,
            {},
            {
            }
        )
    }
}

private class RoomsProvider : PreviewParameterProvider<RoomInfo> {
    override val values: Sequence<RoomInfo>
        get() = sequence {
            val p = Path(
                com.storyteller_f.a.app.BuildKonfig.PROJECT_PATH,
                "../../AData/data/preset_room.json"
            )
            if (SystemFileSystem.exists(p)) {
                val content = SystemFileSystem.source(p).buffered().readString()
                val value = Json.decodeFromString<PresetValue>(content)
                yieldAll(value.roomData.orEmpty().map {
                    RoomInfo.EMPTY.copy(name = it.name)
                })
            }
        }
}

@Preview
@Composable
private fun PreviewRooms(@PreviewParameter(RoomsProvider::class) roomInfo: RoomInfo) {
    RoomCell(roomInfo = roomInfo)
}

@Preview
@Composable
private fun PreviewInputGroup() {
    val roomInfo = RoomInfo.EMPTY
    InputGroupInternal(
        roomInfo.id,
        ObjectType.ROOM,
        "test",
        MaterialTheme.colorScheme.tertiaryContainer,
        null,
        {},
        {
        }
    )
}
