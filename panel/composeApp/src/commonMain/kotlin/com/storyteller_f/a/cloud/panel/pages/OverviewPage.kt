package com.storyteller_f.a.cloud.panel.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.cloud.panel.LocalPanelNav
import com.storyteller_f.a.cloud.panel.common.OverviewViewModel
import com.storyteller_f.a.cloud.panel.common.createPanelOverviewViewModel
import com.storyteller_f.shared.model.PanelOverview
import nl.jacobras.humanreadable.HumanReadable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter
import org.jetbrains.compose.ui.tooling.preview.PreviewParameterProvider

@Composable
fun OverviewPage() {
    val viewModel = createPanelOverviewViewModel()
    OverviewPageInternal(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewPageInternal(viewModel: OverviewViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text("Overview")
            })
        }
    ) {
        Box(modifier = Modifier.padding(top = it.calculateTopPadding()).padding(16.dp)) {
            StateView(viewModel.handler) {
                OverviewFlow(it)
            }
        }
    }
}

class OverviewFlowPreviewProvider : PreviewParameterProvider<PanelOverview> {
    override val values: Sequence<PanelOverview>
        get() = sequenceOf(
            PanelOverview(
                1,
                2,
                3,
                4,
                5,
                6,
                7000
            )
        )
}

@Preview
@Composable
private fun OverviewFlow(@PreviewParameter(OverviewFlowPreviewProvider::class) panelOverview: PanelOverview) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UserCountOverviewCell(panelOverview)
        CommunityCountOverviewCell(panelOverview)
        TopicCountOverviewCell(panelOverview)
        PrivateRoomCountOverviewCell(panelOverview)
        CommunityRoomCountOverviewCell(panelOverview)
        FileCountOverviewCell(panelOverview)
        FileVolumeOverviewCell(panelOverview)
    }
}

@Composable
fun UserCountOverviewCell(panelOverview: PanelOverview) {
    val panelNav = LocalPanelNav.current
    Card(onClick = {
        panelNav.gotoAllUsers()
    }) {
        Box(modifier = Modifier.padding(16.dp)) {
            val text = remember(panelOverview.userCount) {
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)) {
                        append(panelOverview.userCount.toString())
                    }
                    append(" 个用户")
                }
            }
            Text(text)
        }
    }
}

@Composable
fun CommunityCountOverviewCell(panelOverview: PanelOverview) {
    Card {
        Box(modifier = Modifier.padding(16.dp)) {
            val text = remember(panelOverview.communityCount) {
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)) {
                        append(panelOverview.communityCount.toString())
                    }
                    append(" 个社区")
                }
            }
            Text(text)
        }
    }
}

@Composable
fun TopicCountOverviewCell(panelOverview: PanelOverview) {
    Card {
        Box(modifier = Modifier.padding(16.dp)) {
            val text = remember(panelOverview.topicCount) {
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)) {
                        append(panelOverview.topicCount.toString())
                    }
                    append(" 个话题")
                }
            }
            Text(text)
        }
    }
}

@Composable
fun PrivateRoomCountOverviewCell(panelOverview: PanelOverview) {
    Card {
        Box(modifier = Modifier.padding(16.dp)) {
            val text = remember(panelOverview.privateRoomCount) {
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)) {
                        append(panelOverview.privateRoomCount.toString())
                    }
                    append(" 个私有聊天室")
                }
            }
            Text(text)
        }
    }
}

@Composable
fun CommunityRoomCountOverviewCell(panelOverview: PanelOverview) {
    Card {
        Box(modifier = Modifier.padding(16.dp)) {
            val text = remember(panelOverview.communityRoomCount) {
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)) {
                        append(panelOverview.communityRoomCount.toString())
                    }
                    append(" 个公开聊天室")
                }
            }
            Text(text)
        }
    }
}

@Composable
fun FileCountOverviewCell(panelOverview: PanelOverview) {
    Card {
        Box(modifier = Modifier.padding(16.dp)) {
            val text = remember(panelOverview.fileCount) {
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)) {
                        append(panelOverview.fileCount.toString())
                    }
                    append(" 个文件")
                }
            }
            Text(text)
        }
    }
}

@Composable
fun FileVolumeOverviewCell(panelOverview: PanelOverview) {
    Card {
        Box(modifier = Modifier.padding(16.dp)) {
            val text = remember(panelOverview.fileVolume) {
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)) {
                        append(HumanReadable.fileSize(panelOverview.fileVolume))
                    }
                    append(" 文件")
                }
            }
            Text(text)
        }
    }
}
