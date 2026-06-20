package com.storyteller_f.a.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.storyteller_f.a.app.components.InteractionRowInternal
import com.storyteller_f.a.app.components.SettingOptionResettableView
import com.storyteller_f.a.app.components.SettingOptionView
import com.storyteller_f.a.app.core.components.ButtonNav
import com.storyteller_f.a.app.core.components.GlobalDialogContext
import com.storyteller_f.a.app.core.components.GlobalDialogController
import com.storyteller_f.a.app.core.components.GlobalDialogState
import com.storyteller_f.a.app.core.components.InfoTable
import com.storyteller_f.a.app.core.components.MediaObjectBlock
import com.storyteller_f.a.app.core.components.Pill
import com.storyteller_f.a.app.core.components.PrivateKeyEditor
import com.storyteller_f.a.app.core.components.SheetContainer
import com.storyteller_f.a.app.core.components.SignInButton
import com.storyteller_f.a.app.pages.file.FixedProgress
import com.storyteller_f.a.app.pages.file.UploadItem
import com.storyteller_f.a.app.pages.room.PrimaryRoomCell
import com.storyteller_f.a.app.pages.room.UnboundedRoomCell
import com.storyteller_f.a.app.pages.topic.CommonTopicCellInternal
import com.storyteller_f.a.app.pages.topic.TopicCellInternal
import com.storyteller_f.a.app.pages.topic.UserTopicCellInternal
import com.storyteller_f.a.app.pages.user.UserFavoriteCell
import com.storyteller_f.a.app.pages.user.UserSubscriptionCell
import com.storyteller_f.a.app.ui.theme.AppTheme
import com.storyteller_f.a.client.core.SimpleUserSessionManager
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserFavoriteInfo
import com.storyteller_f.shared.model.UserSubscriptionInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.storage.UploadInfo
import com.storyteller_f.storage.UploadStatus
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.datetime.LocalDateTime

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun ButtonNavScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            ButtonNav(Icons.Default.Settings, "Settings")
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun PillScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(text = "Selected", icon = Icons.Default.Lock, selected = true) {}
                Pill(text = "Emoji", emoji = "A", selected = false) {}
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun SignInButtonScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            SignInButton()
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun MediaObjectBlockScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            MediaObjectBlock {
                Text("Media block")
                Text("Static content for snapshot testing", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun InfoTableScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            InfoTable(
                listOf(
                    "Name" to "Snapshot fixture",
                    "Status" to "Ready",
                    "Scope" to "app/core"
                )
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun SheetContainerScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            SheetContainer {
                ButtonNav(Icons.Default.Edit, "Edit profile")
                ButtonNav(Icons.Default.Lock, "Security")
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun PrivateKeyEditorScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            PrivateKeyEditor(
                privateKey = "private-key-preview",
                encryptionPrivateKey = null,
                enableRandom = false,
                algo = com.storyteller_f.shared.model.AlgoType.P256,
                onAlgoChange = {},
                onConfirmPrivateKey = {},
                onConfirmEncryptionPrivateKey = {},
                onCancel = {}
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun CommonTopicCellScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            CommonTopicCellInternal(sampleTopic()) {
                Text("Topic shell", modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun TopicCellScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            TopicCellInternal(sampleTopic())
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun UserTopicCellScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            UserTopicCellInternal(sampleTopic(commentCount = 5, hasComment = true), supportPin = true)
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun InteractionRowScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            InteractionRowInternal(
                data = emptyList(),
                topicInfo = sampleTopic(commentCount = 3, hasComment = true),
                startAddComment = {},
                startAddReaction = {}
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun PrimaryRoomCellScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            PrimaryRoomCell(sampleRoom().copy(latestTopic = 12, lastRead = 3))
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun UnboundedRoomCellScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            UnboundedRoomCell(sampleRoom())
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun UserFavoriteCellScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            UserFavoriteCell(sampleFavorite())
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun UserSubscriptionCellScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            UserSubscriptionCell(sampleSubscription())
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun SettingOptionViewScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            SettingOptionView("Theme", onClick = {}) {
                Text("System")
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun SettingOptionResettableViewScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            SettingOptionResettableView("Message font size", supportReset = true, onClick = {}) {
                Text("16sp")
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun UploadItemScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            UploadItem(sampleUpload())
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@Composable
fun FixedProgressScreenshot() {
    ScreenshotAppTheme {
        PaddedPreview {
            FixedProgress("89.99 %")
        }
    }
}

@Composable
private fun PaddedPreview(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
        content()
    }
}

@Composable
private fun ScreenshotAppTheme(content: @Composable () -> Unit) {
    AppTheme(dynamicColor = false) {
        CompositionLocalProvider(LocalGlobalDialog provides PreviewGlobalDialog) {
            Surface(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

private fun sampleTopic(
    commentCount: Long = 2,
    hasComment: Boolean = false,
) = TopicInfo.EMPTY.copy(
    id = 101,
    content = TopicContent.Plain("Snapshot topic content with enough text to exercise wrapping."),
    createdTime = fixedTime(),
    commentCount = commentCount,
    reactionCount = 4,
    hasComment = hasComment,
    lastModifiedTime = fixedTime(),
    extension = TopicInfo.Extension()
)

private fun sampleRoom() = RoomInfo.EMPTY.copy(
    id = 301,
    createdTime = fixedTime(),
    name = "Snapshot room",
    memberCount = 12
)

private fun sampleFavorite() = UserFavoriteInfo.EMPTY.copy(
    createdTime = fixedTime(),
    objectType = ObjectType.TOPIC,
    extensions = UserFavoriteInfo.Extensions(topicInfo = sampleTopic())
)

private fun sampleSubscription() = UserSubscriptionInfo.EMPTY.copy(
    createdTime = fixedTime(),
    objectType = ObjectType.TOPIC,
    extensions = UserSubscriptionInfo.Extensions(topicInfo = sampleTopic(commentCount = 7, hasComment = true))
)

private fun sampleUpload() = UploadInfo.EMPTY.copy(
    id = 401,
    pathHash = "snapshot",
    path = "/uploads/story.txt",
    progress = 80,
    chunkProgress = 40,
    total = 100,
    status = UploadStatus.UPLOADING,
    message = "Uploading",
    name = "story.txt",
    contentType = "text/plain",
)

private object PreviewGlobalDialog : AppGlobalDialogController {
    override val state: MutableState<PersistentList<GlobalDialogState>> = mutableStateOf(persistentListOf())
    override val context = GlobalDialogContext(MutableSharedFlow(), SimpleUserSessionManager.EMPTY)

    override suspend fun <T> useResult(block: suspend GlobalDialogController<GlobalDialogContext<SimpleUserSessionManager>>.() -> Result<T>): Result<T> {
        return block()
    }

    override fun emitProgress(block: (GlobalDialogState.Loading) -> GlobalDialogState.Loading) = Unit
}

private fun fixedTime() = LocalDateTime(2026, 1, 2, 3, 4, 5)
