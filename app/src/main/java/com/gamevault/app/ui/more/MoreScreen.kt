package com.gamevault.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Entries of the "More" hub. Navigation to the matching route lives in the
 * nav host so the screen itself stays navigation-agnostic.
 */
enum class MoreEntry {
    COLLECTIONS,
    APPEARANCE,
    STATISTICS,
    SECURITY,
    ADVANCED,
    DATA_STORAGE,
    SETTINGS,
    ABOUT,
}

private data class MoreItem(
    val entry: MoreEntry,
    val label: String,
    val supporting: String,
    val icon: ImageVector,
)

private val moreItems = listOf(
    MoreItem(MoreEntry.COLLECTIONS, "Collections", "Manage your game collections", Icons.Default.CollectionsBookmark),
    MoreItem(MoreEntry.APPEARANCE, "Appearance", "Theme, palettes and AMOLED", Icons.Default.Palette),
    MoreItem(MoreEntry.STATISTICS, "Statistics", "Play time, progress and totals", Icons.Default.Leaderboard),
    MoreItem(MoreEntry.SECURITY, "Security & Privacy", "Cookie, incognito and privacy", Icons.Default.Lock),
    MoreItem(MoreEntry.ADVANCED, "Advanced", "Library badges and special options", Icons.Default.Build),
    MoreItem(MoreEntry.DATA_STORAGE, "Data & Storage", "Backup and storage usage", Icons.Default.Storage),
    MoreItem(MoreEntry.SETTINGS, "Settings", "Library defaults and request pacing", Icons.Default.Settings),
    MoreItem(MoreEntry.ABOUT, "About", "Version and legal", Icons.Default.Info),
)

/**
 * "More" tab — hub screen with entries for secondary app sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onItemClick: (MoreEntry) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("More") }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            items(moreItems.size) { index ->
                val item = moreItems[index]
                ListItem(
                    headlineContent = { Text(item.label) },
                    supportingContent = { Text(item.supporting) },
                    leadingContent = {
                        Icon(item.icon, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onItemClick(item.entry) },
                )
            }
        }
    }
}