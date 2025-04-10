import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.storyteller_f.shared.model.CommunityInfo

private class CommunitiesProvider : PreviewParameterProvider<List<CommunityInfo>> {
    override val values: Sequence<List<CommunityInfo>>
        get() = sequence {
            yield(listOf(CommunityInfo.EMPTY))
        }
}

@Preview
@Composable
private fun PreviewCommunities(@PreviewParameter(CommunitiesProvider::class) communities: List<CommunityInfo>) {

}
