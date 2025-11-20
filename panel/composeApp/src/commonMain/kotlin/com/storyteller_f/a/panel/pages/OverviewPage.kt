package com.storyteller_f.a.panel.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.panel.LocalPanelNav
import com.storyteller_f.a.panel.Res
import com.storyteller_f.a.panel.common.OverviewViewModel
import com.storyteller_f.a.panel.common.createPanelOverviewViewModel
import com.storyteller_f.a.panel.communities_suffix
import com.storyteller_f.a.panel.files_suffix
import com.storyteller_f.a.panel.overview
import com.storyteller_f.a.panel.private_rooms_suffix
import com.storyteller_f.a.panel.public_rooms_suffix
import com.storyteller_f.a.panel.titles_suffix
import com.storyteller_f.a.panel.topics_suffix
import com.storyteller_f.a.panel.users_suffix
import com.storyteller_f.shared.model.PanelOverview
import nl.jacobras.humanreadable.HumanReadable
import org.jetbrains.compose.resources.stringResource
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
    val panelNav = LocalPanelNav.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(Res.string.overview))
                },
                navigationIcon = {
                    IconButton({ panelNav.open() }) {
                        Icon(
                            Icons.Default.Menu,
                            null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
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
                7000,
                8000,
            )
        )
}

@Preview
@Composable
private fun OverviewFlow(@PreviewParameter(OverviewFlowPreviewProvider::class) panelOverview: PanelOverview) {
    Column(modifier = Modifier.fillMaxSize()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UserCountOverviewCell(panelOverview)
            CommunityCountOverviewCell(panelOverview)
            TopicCountOverviewCell(panelOverview)
            TitleCountOverviewCell(panelOverview)
            PrivateRoomCountOverviewCell(panelOverview)
            CommunityRoomCountOverviewCell(panelOverview)
            FileCountOverviewCell(panelOverview)
        }
    }
}

@Composable
fun UserCountOverviewCell(panelOverview: PanelOverview) {
    val panelNav = LocalPanelNav.current
    Card(onClick = {
        panelNav.gotoAllUsers()
    }) {
        Box(modifier = Modifier.padding(16.dp)) {
            val suffix = stringResource(Res.string.users_suffix)
            val text = remember(panelOverview.userCount, suffix) {
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)) {
                        append(panelOverview.userCount.toString())
                    }
                    append(suffix)
                }
            }
            Text(text)
        }
    }
}

@Composable
fun CommunityCountOverviewCell(panelOverview: PanelOverview) {
    val panelNav = LocalPanelNav.current
    Card(onClick = { panelNav.gotoAllCommunities() }) {
        Box(modifier = Modifier.padding(16.dp)) {
            val suffix = stringResource(Res.string.communities_suffix)
            val text = remember(panelOverview.communityCount, suffix) {
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)) {
                        append(panelOverview.communityCount.toString())
                    }
                    append(suffix)
                }
            }
            Text(text)
        }
    }
}

@Composable
fun TopicCountOverviewCell(panelOverview: PanelOverview) {
    val panelNav = LocalPanelNav.current
    Card(onClick = { panelNav.gotoAllTopics() }) {
        Box(modifier = Modifier.padding(16.dp)) {
            val suffix = stringResource(Res.string.topics_suffix)
            val text = remember(panelOverview.topicCount, suffix) {
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)) {
                        append(panelOverview.topicCount.toString())
                    }
                    append(suffix)
                }
            }
            Text(text)
        }
    }
}

@Composable
fun TitleCountOverviewCell(panelOverview: PanelOverview) {
    val panelNav = LocalPanelNav.current
    Card(onClick = { panelNav.gotoAllTitles() }) {
        Box(modifier = Modifier.padding(16.dp)) {
            val suffix = stringResource(Res.string.titles_suffix)
            val text = remember(panelOverview.titleCount, suffix) {
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)) {
                        append(panelOverview.titleCount.toString())
                    }
                    append(suffix)
                }
            }
            Text(text)
        }
    }
}

@Composable
fun PrivateRoomCountOverviewCell(panelOverview: PanelOverview) {
    val panelNav = LocalPanelNav.current
    Card(onClick = { panelNav.gotoAllPrivateRooms() }) {
        Box(modifier = Modifier.padding(16.dp)) {
            val suffix = stringResource(Res.string.private_rooms_suffix)
            val text = remember(panelOverview.privateRoomCount, suffix) {
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)) {
                        append(panelOverview.privateRoomCount.toString())
                    }
                    append(suffix)
                }
            }
            Text(text)
        }
    }
}

@Composable
fun CommunityRoomCountOverviewCell(panelOverview: PanelOverview) {
    val panelNav = LocalPanelNav.current
    Card(onClick = { panelNav.gotoAllPublicRooms() }) {
        Box(modifier = Modifier.padding(16.dp)) {
            val suffix = stringResource(Res.string.public_rooms_suffix)
            val text = remember(panelOverview.communityRoomCount, suffix) {
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)) {
                        append(panelOverview.communityRoomCount.toString())
                    }
                    append(suffix)
                }
            }
            Text(text)
        }
    }
}

@Composable
fun FileCountOverviewCell(panelOverview: PanelOverview) {
    val panelNav = LocalPanelNav.current
    Card(onClick = { panelNav.gotoAllFiles() }) {
        Box(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val suffix = stringResource(Res.string.files_suffix)
                Text(remember(panelOverview.fileCount, suffix) {
                    buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(panelOverview.fileCount.toString())
                        }
                        append(suffix)
                    }
                })

                Text(remember(panelOverview.fileVolume) {
                    buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(HumanReadable.fileSize(panelOverview.fileVolume))
                        }
                    }
                })
            }
        }
    }
}
