package com.storyteller_f.a.app.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBar
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
        SearchBar(query, {
            query = it
        }, {
        }, active, {
            active = it
        }, trailingIcon = {
            MyIcon(40.dp)
        }, leadingIcon = {
            leadingIcon()
        }, modifier = Modifier.align(Alignment.Center)) {
        }
    }
}
