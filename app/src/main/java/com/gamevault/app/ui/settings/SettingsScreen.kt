package com.gamevault.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamevault.app.BuildConfig
import com.gamevault.app.data.settings.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Appearance", "Library", "Collections", "Backup")

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportBackup(context, it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importBackup(context, it) }
    }

    LaunchedEffect(state.backupUi.lastBackupResult) {
        state.backupUi.lastBackupResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearBackupResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                when (selectedTab) {
                    0 -> {
                        // Appearance
                        item {
                            ThemeSection(
                                currentTheme = state.themeMode,
                                amoledDark = state.amoledDark,
                                gifAutoplay = state.gifAutoplay,
                                onThemeSelected = viewModel::setThemeMode,
                                onAmoledToggle = viewModel::setAmoledDark,
                                onGifAutoplayToggle = viewModel::setGifAutoplay,
                            )
                        }

                        // About
                        item {
                            AboutSection()
                        }
                    }

                    1 -> {
                        // Library
                        item {
                            DefaultLocationSection(
                                defaultCollectionId = state.defaultCollectionId,
                                collections = state.collections,
                                onSelected = viewModel::setDefaultCollectionId,
                            )
                        }
                    }

                    2 -> {
                        // Collections
                        item {
                            CollectionManagementSection(
                                collections = state.collections,
                                showCreateDialog = state.showCreateDialog,
                                showRenameDialog = state.showRenameDialog,
                                renameTarget = state.renameTarget,
                                newCollectionName = state.newCollectionName,
                                onCreateNew = viewModel::showCreateDialog,
                                onShowRename = viewModel::showRenameDialog,
                                onDelete = viewModel::deleteCollection,
                                onDismissCreate = viewModel::dismissCreateDialog,
                                onDismissRename = viewModel::dismissRenameDialog,
                                onSetNewName = viewModel::setNewCollectionName,
                                onCreateCollection = viewModel::createCollection,
                                onRenameCollection = viewModel::renameCollection,
                            )
                        }
                    }

                    3 -> {
                        // Backup
                        item {
                            BackupSection(
                                isExporting = state.backupUi.isExporting,
                                isImporting = state.backupUi.isImporting,
                                onExport = { exportLauncher.launch("gamevault_backup.json") },
                                onImport = { importLauncher.launch(arrayOf("application/json")) },
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun ThemeSection(
    currentTheme: ThemeMode,
    amoledDark: Boolean,
    gifAutoplay: Boolean,
    onThemeSelected: (ThemeMode) -> Unit,
    onAmoledToggle: (Boolean) -> Unit,
    onGifAutoplayToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.SYSTEM -> "System default"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .selectable(
                                selected = currentTheme == mode,
                                onClick = { onThemeSelected(mode) },
                                role = Role.RadioButton,
                            )
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentTheme == mode,
                            onClick = null,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "AMOLED Dark (pure black)",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = amoledDark,
                    onCheckedChange = onAmoledToggle,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GIF Autoplay",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Automatically play animated cover images",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                Switch(
                    checked = gifAutoplay,
                    onCheckedChange = onGifAutoplayToggle,
                )
            }
        }
    }
}

@Composable
private fun CollectionManagementSection(
    collections: List<CollectionWithCount>,
    showCreateDialog: Boolean,
    showRenameDialog: Boolean,
    renameTarget: com.gamevault.app.domain.model.Collection?,
    newCollectionName: String,
    onCreateNew: () -> Unit,
    onShowRename: (com.gamevault.app.domain.model.Collection) -> Unit,
    onDelete: (com.gamevault.app.domain.model.Collection) -> Unit,
    onDismissCreate: () -> Unit,
    onDismissRename: () -> Unit,
    onSetNewName: (String) -> Unit,
    onCreateCollection: () -> Unit,
    onRenameCollection: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Collections", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onCreateNew) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (collections.isEmpty()) {
                Text(
                    text = "No collections yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            } else {
                collections.forEach { cwc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShowRename(cwc.collection) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cwc.collection.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${cwc.gameCount} games",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        IconButton(onClick = { onShowRename(cwc.collection) }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Rename",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = { onDelete(cwc.collection) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (cwc != collections.last()) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = onDismissCreate,
            title = { Text("New Collection") },
            text = {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = onSetNewName,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onCreateCollection,
                    enabled = newCollectionName.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = onDismissCreate) { Text("Cancel") }
            },
        )
    }

    if (showRenameDialog && renameTarget != null) {
        AlertDialog(
            onDismissRequest = onDismissRename,
            title = { Text("Rename Collection") },
            text = {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = onSetNewName,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onRenameCollection,
                    enabled = newCollectionName.isNotBlank(),
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = onDismissRename) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DefaultLocationSection(
    defaultCollectionId: Long?,
    collections: List<CollectionWithCount>,
    onSelected: (Long?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Default save location", style = MaterialTheme.typography.titleSmall)
        Text(
            "New games added by URL will land here",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        // "Library" (root) option
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (defaultCollectionId == null) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                )
                .clickable { onSelected(null) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.CollectionsBookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Library (root)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (defaultCollectionId == null) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Each collection
        collections.forEach { cwc ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (defaultCollectionId == cwc.collection.id) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    .clickable { onSelected(cwc.collection.id) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(cwc.collection.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${cwc.gameCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (defaultCollectionId == cwc.collection.id) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun BackupSection(
    isExporting: Boolean,
    isImporting: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Backup & Restore", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting,
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isExporting) "Exporting..." else "Export Backup")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isImporting,
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isImporting) "Importing..." else "Import Backup")
            }
        }
    }
}

@Composable
private fun AboutSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("About", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "GameVault",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
