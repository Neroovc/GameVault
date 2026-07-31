package com.gamevault.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * "More" tab — overflow menu screen with entries for secondary app sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onSettingsClick: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("More") }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Settings") },
                    supportingContent = { Text("Appearance, library, collections, backup") },
                    leadingContent = {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onSettingsClick() },
                )
            }
        }
    }
}
