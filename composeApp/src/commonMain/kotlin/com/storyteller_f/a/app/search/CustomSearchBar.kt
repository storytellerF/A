package com.storyteller_f.a.app.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.compontents.MyIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSearchBar(leadingIcon: @Composable () -> Unit) {
    var query by remember {
        mutableStateOf("")
    }
    var active by remember {
        mutableStateOf(false)
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        val onActiveChange = { newValue: Boolean ->
            active = newValue
        }

        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = {
                        query = it
                    },
                    onSearch = {
                    },
                    expanded = active,
                    onExpandedChange = onActiveChange,
                    enabled = true,
                    placeholder = null,
                    leadingIcon = {
                        leadingIcon()
                    },
                    trailingIcon = {
                        MyIcon(40.dp)
                    },
                    interactionSource = null,
                )
            },
            expanded = active,
            onExpandedChange = onActiveChange,
            modifier = Modifier.align(Alignment.Center),
            shape = SearchBarDefaults.inputFieldShape,
            colors = SearchBarDefaults.colors(),
            tonalElevation = SearchBarDefaults.TonalElevation,
            shadowElevation = SearchBarDefaults.ShadowElevation,
            windowInsets = SearchBarDefaults.windowInsets,
            content = {
            },
        )
    }
}
