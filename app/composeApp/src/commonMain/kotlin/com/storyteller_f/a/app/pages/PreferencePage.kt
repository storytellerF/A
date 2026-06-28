package com.storyteller_f.a.app.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.core.components.CustomIcon
import com.storyteller_f.a.app.core.components.IconRes
import com.storyteller_f.a.app.core.components.LocalToaster
import com.storyteller_f.a.app.core.components.catchingResult
import com.storyteller_f.a.app.current_selected
import com.storyteller_f.a.app.home_start_destination
import com.storyteller_f.a.app.home_start_destination_communities
import com.storyteller_f.a.app.home_start_destination_rooms
import com.storyteller_f.a.app.home_start_destination_world
import com.storyteller_f.a.app.pages.topic.TopicTranslateSheet
import com.storyteller_f.a.app.service.buildGPT
import com.storyteller_f.a.app.service.getGPTModelDirectory
import com.storyteller_f.a.app.translate_model
import com.storyteller_f.a.app.try_button
import com.storyteller_f.a.app.ui.MaterialSymbolsOutlined
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.strabled.composepreferences.PreferenceScreen
import com.strabled.composepreferences.PreferenceTheme
import com.strabled.composepreferences.getPreference
import com.strabled.composepreferences.preferences.BottomSheetListPreference
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.name
import org.jetbrains.compose.resources.stringResource

@Composable
fun PreferencePage() {
    PreferenceScreen(
        theme = PreferenceTheme.colorScheme.copy(trailingContentColor = MaterialTheme.colorScheme.onPrimary)
    ) {
        preferenceItem {
            HomeStartDestinationPreferenceItem()
        }
        preferenceItem {
            TranslateModelPreferenceItem()
        }
    }
}

@Composable
private fun HomeStartDestinationPreferenceItem() {
    BottomSheetListPreference(
        getPreference(HOME_START_DESTINATION_PREFERENCE_KEY),
        title = stringResource(Res.string.home_start_destination),
        items = mapOf(
            stringResource(Res.string.home_start_destination_world) to HOME_START_DESTINATION_WORLD,
            stringResource(Res.string.home_start_destination_communities) to HOME_START_DESTINATION_COMMUNITIES,
            stringResource(Res.string.home_start_destination_rooms) to HOME_START_DESTINATION_ROOMS,
        ),
        summary = {
            Text(stringResource(Res.string.current_selected, homeStartDestinationLabel(it)))
        },
        leadingIcon = {
            CustomIcon(IconRes.Font(MaterialSymbolsOutlined.Home))
        },
        useSelectedInSummary = true,
    )
}

@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslateModelPreferenceItem() {
    val gpt = remember {
        buildGPT()
    }
    var showSheet by remember {
        mutableStateOf(false)
    }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val toast = LocalToaster.current
    val globalDialogController = LocalGlobalDialog.current
    val models by gpt.models(scope).collectAsState(emptyList())
    BottomSheetListPreference(
        getPreference("gpt_model"),
        title = stringResource(Res.string.translate_model),
        items = models.associate {
            it.value to it.key
        },
        summary = {
            if (it.isNullOrBlank()) {
                getGPTModelDirectory().toString()
                Text("support ${gpt.supportList}")
            } else {
                Text(stringResource(Res.string.current_selected, it.substringAfterLast('/').substringAfterLast('\\')))
            }
        },
        leadingIcon = {
            CustomIcon(IconRes.Font(MaterialSymbolsOutlined.Translate))
        },
        useSelectedInSummary = true,
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({
                    globalDialogController.catchingResult(scope) {
                        FileKit.openFilePicker()?.let { file ->
                            gpt.importModel(file).getOrThrow()
                            toast.showMessage("${file.name} imported")
                        }
                    }
                }) {
                    Text("Import")
                }
                Button({
                    showSheet = true
                }) {
                    Text(stringResource(Res.string.try_button))
                }
            }
        }
    )

    TopicTranslateSheet(
        showSheet,
        sheetState,
        TopicInfo.EMPTY.copy(content = TopicContent.Plain("""Jonas, a 12-year-old boy, lives in 
            |a community isolated from all except a few similar towns, 
            |where everyone has an assigned role. 
            |With the annual Ceremony of Twelve upcoming, 
            |he is nervous, for there he will be assigned his life's work. 
            |He seeks reassurance from his father, 
            |a Nurturer (who cares for the infants in the Community) and his mother, 
            |a high-ranking official in the Department of Justice. 
            |He is told by his parents that the Elders, 
            |who assign the children their careers, are always right. """.trimMargin()))
    ) {
        showSheet = false
    }
}
