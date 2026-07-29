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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.storyteller_f.a.app.components.SettingOptionResettableView
import com.storyteller_f.a.app.components.SettingOptionView
import com.storyteller_f.a.app.pages.file.FixedProgress
import com.storyteller_f.a.app.pages.room.PrimaryRoomCell
import com.storyteller_f.a.app.pages.room.UnboundedRoomCell
import com.storyteller_f.a.app.ui.theme.AppTheme
import com.storyteller_f.a.client.compose_core.components.ButtonNav
import com.storyteller_f.a.client.compose_core.components.InfoTable
import com.storyteller_f.a.client.compose_core.components.MediaObjectBlock
import com.storyteller_f.a.client.compose_core.components.Pill
import com.storyteller_f.a.client.compose_core.components.PrivateKeyEditor
import com.storyteller_f.a.client.compose_core.components.SheetContainer
import com.storyteller_f.a.client.compose_core.components.SignInButton
import com.storyteller_f.shared.model.RoomInfo
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
                    "Scope" to "client/composeCore"
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
        Surface(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

private fun sampleRoom() = RoomInfo.EMPTY.copy(
    id = 301,
    createdTime = fixedTime(),
    name = "Snapshot room",
    memberCount = 12
)

private fun fixedTime() = LocalDateTime(2026, 1, 2, 3, 4, 5)
