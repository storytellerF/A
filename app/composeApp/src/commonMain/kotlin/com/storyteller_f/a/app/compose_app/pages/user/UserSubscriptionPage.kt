package com.storyteller_f.a.app.compose_app.pages.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.compose_app.common.SubscriptionsViewModel
import com.storyteller_f.a.app.compose_app.common.getSubscriptionViewModel
import com.storyteller_f.a.app.compose_app.pages.topic.TopicCell
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserFavoriteInfo
import com.storyteller_f.shared.model.UserSubscriptionInfo
import com.storyteller_f.shared.type.ObjectType
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter
import org.jetbrains.compose.ui.tooling.preview.PreviewParameterProvider

@Composable
fun UserSubscriptionPage() {
    val viewModel = getSubscriptionViewModel()
    SubscriptionPageInternal(viewModel)
}

@Composable
fun SubscriptionPageInternal(viewModel: SubscriptionsViewModel) {
    Scaffold { paddingValues ->
        StateView(
            viewModel,
            modifier = Modifier.padding(top = paddingValues.calculateTopPadding())
        ) { items ->
            LazyColumn(contentPadding = PaddingValues(10.dp)) {
                pagingItems(items, {
                    it.id
                }) {
                    UserSubscriptionCell(items[it])
                    HorizontalDivider()
                }
            }
        }
    }
}

class UserSubscriptionPreviewProvider : PreviewParameterProvider<UserFavoriteInfo> {
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
fun UserSubscriptionCell(
    @PreviewParameter(
        UserSubscriptionPreviewProvider::class
    ) userFavoriteInfo: UserSubscriptionInfo?
) {
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Subscribed at ${userFavoriteInfo?.createdTime}",
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        when (userFavoriteInfo?.objectType) {
            ObjectType.TOPIC -> {
                val topicInfo = userFavoriteInfo.extensions?.topicInfo
                if (topicInfo != null) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp).background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(8.dp)
                        )
                    ) {
                        TopicCell(topicInfo)
                    }
                }
            }

            else -> {
            }
        }
    }
}
