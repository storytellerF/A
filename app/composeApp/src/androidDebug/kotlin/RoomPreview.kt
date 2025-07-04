import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.storyteller_f.a.app.compose_app.pages.room.RoomCell
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicInfo

private class MessageListProvider : PreviewParameterProvider<List<TopicInfo>> {
    override val values: Sequence<List<TopicInfo>>
        get() = sequence {
            yield(listOf(TopicInfo.EMPTY))
        }
}

@Composable
@Preview
private fun PreviewRoom(@PreviewParameter(MessageListProvider::class) topicInfos: List<TopicInfo>) {

}

private class RoomsProvider : PreviewParameterProvider<RoomInfo> {
    override val values: Sequence<RoomInfo>
        get() = sequence {
            yield(RoomInfo.EMPTY)
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

}
