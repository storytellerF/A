package com.storyteller_f.a.app.compose_app.pages.user

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.storyteller_f.a.app.compose_app.common.FavoritesViewModel
import com.storyteller_f.a.app.compose_app.common.getFavoriteViewModel
import com.storyteller_f.a.app.compose_app.pages.topic.TopicCell
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserFavoriteInfo
import com.storyteller_f.shared.type.ObjectType
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter
import org.jetbrains.compose.ui.tooling.preview.PreviewParameterProvider

@Composable
fun UserFavoritePage() {
    val viewModel = getFavoriteViewModel()
    FavoritePageInternal(viewModel)
}

@Composable
fun FavoritePageInternal(viewModel: FavoritesViewModel) {
    Scaffold { paddingValues ->
        StateView(viewModel, modifier = Modifier.padding(top = paddingValues.calculateTopPadding())) { items ->
            LazyColumn {
                pagingItems(items, {
                    it.id
                }) {
                    UserFavoriteCell(items[it])
                }
            }
        }
    }
}

class UserFavoritePreviewProvider : PreviewParameterProvider<UserFavoriteInfo> {
    override val values: Sequence<UserFavoriteInfo>
        get() = sequenceOf(
            UserFavoriteInfo.EMPTY.copy(
                extensions = UserFavoriteInfo.Extensions(
                    TopicInfo.EMPTY.copy(content = TopicContent.Plain("hello"))
                )
            )
        )
}

@Preview(widthDp = 300)
@Composable
fun UserFavoriteCell(@PreviewParameter(UserFavoritePreviewProvider::class) userFavoriteInfo: UserFavoriteInfo?) {
    when (userFavoriteInfo?.objectType) {
        ObjectType.TOPIC -> {
            val topicInfo = userFavoriteInfo.extensions?.topicInfo
            if (topicInfo != null) {
                TopicCell(topicInfo)
            }
        }

        else -> {
        }
    }
}
