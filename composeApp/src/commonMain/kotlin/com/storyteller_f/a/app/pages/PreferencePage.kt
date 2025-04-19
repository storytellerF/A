package com.storyteller_f.a.app.pages

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.storyteller_f.a.app.compontents.CustomIcon
import com.storyteller_f.a.app.compontents.IconRes
import com.storyteller_f.a.app.pages.topic.TopicTranslateSheet
import com.storyteller_f.a.app.service.buildGPT
import com.storyteller_f.a.app.ui.MaterialSymbolsOutlined
import com.storyteller_f.shared.model.TopicInfo
import com.strabled.composepreferences.PreferenceScreen
import com.strabled.composepreferences.ProvideDataStoreManager
import com.strabled.composepreferences.getPreference
import com.strabled.composepreferences.preferences.BottomSheetListPreference
import com.strabled.composepreferences.setPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencePage() {
    ProvideDataStoreManager {
        setPreferences {
            "gpt_model" defaultValue ""
        }
        PreferenceScreen {
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
                        CustomIcon(IconRes.Font(MaterialSymbolsOutlined.Translate), "translate")
                    },
                    useSelectedInSummary = true,
                    trailingContent = {
                        Button({
                            showSheet = true
                        }) {
                            Text("Try", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                )

                TopicTranslateSheet(
                    showSheet,
                    sheetState,
                    TopicInfo.EMPTY.copy(content = com.storyteller_f.shared.model.TopicContent.Plain("hello"))
                ) {
                    showSheet = false
                }
            }
        }
    }
}
