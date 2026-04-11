package com.storyteller_f.a.app.pages

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
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.core.components.CustomIcon
import com.storyteller_f.a.app.core.components.IconRes
import com.storyteller_f.a.app.current_selected
import com.storyteller_f.a.app.home_start_destination
import com.storyteller_f.a.app.home_start_destination_communities
import com.storyteller_f.a.app.home_start_destination_rooms
import com.storyteller_f.a.app.home_start_destination_world
import com.storyteller_f.a.app.pages.topic.TopicTranslateSheet
import com.storyteller_f.a.app.service.buildGPT
import com.storyteller_f.a.app.translate_model
import com.storyteller_f.a.app.try_button
import com.storyteller_f.a.app.ui.MaterialSymbolsOutlined
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.strabled.composepreferences.PreferenceScreen
import com.strabled.composepreferences.PreferenceTheme
import com.strabled.composepreferences.getPreference
import com.strabled.composepreferences.preferences.BottomSheetListPreference
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslateModelPreferenceItem() {
    var showSheet by remember {
        mutableStateOf(false)
    }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val models by buildGPT().models(scope).collectAsState(emptyList())
    BottomSheetListPreference(
        getPreference("gpt_model"),
        title = stringResource(Res.string.translate_model),
        items = models.associate {
            it.value to it.key
        },
        summary = {
            if (!it.isNullOrBlank()) {
                Text(stringResource(Res.string.current_selected, it))
            }
        },
        leadingIcon = {
            CustomIcon(IconRes.Font(MaterialSymbolsOutlined.Translate))
        },
        useSelectedInSummary = true,
        trailingContent = {
            Button({
                showSheet = true
            }) {
                Text(stringResource(Res.string.try_button))
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
