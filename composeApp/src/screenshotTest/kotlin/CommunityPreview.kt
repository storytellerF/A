import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.storyteller_f.shared.model.CommunityInfo

@Preview
@Composable
private fun PreviewCommunity(@PreviewParameter(CommunityProvider::class) communityInfo: CommunityInfo) {
}

@Preview
@Composable
private fun PreviewCommunityPage() {

}

private class CommunityProvider : PreviewParameterProvider<CommunityInfo> {
    override val values: Sequence<CommunityInfo>
        get() = sequence {
            yield(CommunityInfo.EMPTY)
        }
}
