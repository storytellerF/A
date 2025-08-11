import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.storyteller_f.a.app.compose_app.pages.room.InputGroupInternal
import com.storyteller_f.a.app.compose_app.pages.room.RoomCell
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType

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
    InputGroupInternal("", Color.Black, {}, {

    }, ObjectTuple(0, ObjectType.USER)) { }
}
