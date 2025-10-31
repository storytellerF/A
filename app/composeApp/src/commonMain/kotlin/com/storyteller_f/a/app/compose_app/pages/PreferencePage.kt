package com.storyteller_f.a.app.compose_app.pages

import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.storyteller_f.a.app.compose_app.pages.topic.TopicTranslateSheet
import com.storyteller_f.a.app.compose_app.service.buildGPT
import com.storyteller_f.a.app.compose_app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.app.core.compontents.CustomIcon
import com.storyteller_f.a.app.core.compontents.IconRes
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.strabled.composepreferences.PreferenceScreen
import com.strabled.composepreferences.PreferenceTheme
import com.strabled.composepreferences.getPreference
import com.strabled.composepreferences.preferences.BottomSheetListPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencePage() {
    PreferenceScreen(
        theme = PreferenceTheme.colorScheme.copy(trailingContentColor = MaterialTheme.colorScheme.onPrimary)
    ) {
        preferenceItem {
            var showSheet by remember {
                mutableStateOf(false)
            }
            val sheetState = rememberModalBottomSheetState()
            val scope = rememberCoroutineScope()
            val models by buildGPT().models(scope).collectAsState(emptyList())
            BottomSheetListPreference(
                getPreference("gpt_model"),
                title = "Translate Model",
                items = models.associate {
                    it.value to it.key
                },
                summary = {
                    if (!it.isNullOrBlank()) {
                        Text("current selected $it")
                    }
                },
                leadingIcon = {
                    CustomIcon(
                        IconRes.Font(
                            MaterialSymbolsOutlined.Translate
                        )
                    )
                },
                useSelectedInSummary = true,
                trailingContent = {
                    Button({
                        showSheet = true
                    }) {
                        Text("Try")
                    }
                }
            )

            TopicTranslateSheet(
                showSheet,
                sheetState,
                TopicInfo.EMPTY.copy(content = TopicContent.Plain("hello"))
            ) {
                showSheet = false
            }
        }
    }
}
