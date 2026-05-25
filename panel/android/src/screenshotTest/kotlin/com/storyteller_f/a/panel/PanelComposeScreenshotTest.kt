package com.storyteller_f.a.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.android.tools.screenshot.PreviewTest
import com.storyteller_f.a.panel.common.PanelNav
import com.storyteller_f.a.panel.components.InfoTable
import com.storyteller_f.a.panel.components.TopicCell
import com.storyteller_f.a.panel.pages.CommunityCountOverviewCell
import com.storyteller_f.a.panel.pages.CommunityRoomCountOverviewCell
import com.storyteller_f.a.panel.pages.FileCountOverviewCell
import com.storyteller_f.a.panel.pages.PrivateRoomCountOverviewCell
import com.storyteller_f.a.panel.pages.TitleCountOverviewCell
import com.storyteller_f.a.panel.pages.TopicCountOverviewCell
import com.storyteller_f.a.panel.pages.UserCountOverviewCell
import com.storyteller_f.a.panel.ui.theme.PanelTheme
import com.storyteller_f.shared.model.PanelOverview
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import kotlinx.datetime.LocalDateTime

@PreviewTest
@Preview(showBackground = true, widthDp = 240)
@Composable
fun UserCountOverviewCellScreenshot() {
    PanelScreenshotTheme {
        PaddedPreview {
            UserCountOverviewCell(sampleOverview())
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 240)
@Composable
fun CommunityCountOverviewCellScreenshot() {
    PanelScreenshotTheme {
        PaddedPreview {
            CommunityCountOverviewCell(sampleOverview())
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 240)
@Composable
fun TopicCountOverviewCellScreenshot() {
    PanelScreenshotTheme {
        PaddedPreview {
            TopicCountOverviewCell(sampleOverview())
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 240)
@Composable
fun TitleCountOverviewCellScreenshot() {
    PanelScreenshotTheme {
        PaddedPreview {
            TitleCountOverviewCell(sampleOverview())
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 240)
@Composable
fun PrivateRoomCountOverviewCellScreenshot() {
    PanelScreenshotTheme {
        PaddedPreview {
            PrivateRoomCountOverviewCell(sampleOverview())
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 240)
@Composable
fun CommunityRoomCountOverviewCellScreenshot() {
    PanelScreenshotTheme {
        PaddedPreview {
            CommunityRoomCountOverviewCell(sampleOverview())
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 240)
@Composable
fun FileCountOverviewCellScreenshot() {
    PanelScreenshotTheme {
        PaddedPreview {
            FileCountOverviewCell(sampleOverview())
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 480)
@Composable
fun PanelTopicCellScreenshot() {
    PanelScreenshotTheme {
        PaddedPreview {
            TopicCell(
                samplePanelTopic().copy(commentCount = 4, reactionCount = 9, isPin = true),
                PreviewPanelNav
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 480)
@Composable
fun PanelInfoTableScreenshot() {
    PanelScreenshotTheme {
        PaddedPreview {
            InfoTable(
                listOf(
                    "ID" to "501",
                    "Name" to "Panel snapshot fixture",
                    "Status" to "NORMAL",
                    "Owner" to "system"
                )
            )
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
private fun PanelScreenshotTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPanelNav provides PreviewPanelNav) {
        PanelTheme(dynamicColor = false) {
            Surface(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

private fun samplePanelTopic() = TopicInfo.EMPTY.copy(
    id = 501,
    content = TopicContent.Plain("Panel topic row rendered from screenshotTest."),
    createdTime = fixedTime(),
    commentCount = 2,
    reactionCount = 3,
    isEncrypted = true,
    lastModifiedTime = fixedTime(),
)

private fun sampleOverview() = PanelOverview(12, 5, 34, 8, 3, 9, 128, 7)

private object PreviewPanelNav : PanelNav {
    override val drawerState: DrawerState
        get() = error("PreviewPanelNav.drawerState is not used in screenshot previews")
    override val backStack: NavBackStack<NavKey>
        get() = error("PreviewPanelNav.backStack is not used in screenshot previews")

    override fun gotoLogin() = Unit
    override fun gotoOverview() = Unit
    override fun gotoAllUsers() = Unit
    override fun gotoUserDetail(uid: Long) = Unit
    override fun gotoAllCommunities() = Unit
    override fun gotoCommunityDetail(id: Long) = Unit
    override fun gotoAllPublicRooms() = Unit
    override fun gotoRoomDetail(id: Long) = Unit
    override fun gotoAllPrivateRooms() = Unit
    override fun gotoAllTopics() = Unit
    override fun gotoTopicDetail(id: Long) = Unit
    override fun gotoAllFiles() = Unit
    override fun gotoFileDetail(id: Long) = Unit
    override fun gotoAllTitles() = Unit
    override fun gotoTitleDetail(id: Long) = Unit
    override fun gotoTaskRecords() = Unit
    override fun gotoFilePreview(id: Long, url: String, contentType: String, name: String) = Unit
    override fun back() = Unit
    override fun open() = Unit
}

private fun fixedTime() = LocalDateTime(2026, 1, 2, 3, 4, 5)
