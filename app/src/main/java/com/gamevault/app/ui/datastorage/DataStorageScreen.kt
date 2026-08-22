package com.gamevault.app.ui.datastorage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gamevault.app.data.local.BackupOptions
import com.gamevault.app.data.local.RestoreOptions
import com.gamevault.app.data.settings.AutoBackupFrequency
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MIN_KEEP_COUNT = 1
private const val MAX_KEEP_COUNT = 20

private val BackupOptionsSaver = listSaver<BackupOptions, Boolean>(
    save = {
        listOf(
            it.libraryEntries,
            it.collections,
            it.history,
            it.tags,
            it.appSettings,
            it.privateSettings,
        )
    },
    restore = {
        // Collections/history/tags are forced to mirror libraryEntries (locked
        // on with it), so normalize any state saved before that invariant.
        val library = it[0]
        BackupOptions(
            libraryEntries = library,
            collections = library,
            history = library,
            tags = library,
            appSettings = it[4],
            privateSettings = it[5],
        )
    },
)

private val RestoreOptionsSaver = listSaver<RestoreOptions, Boolean>(
    save = {
        listOf(
            it.libraryEntries,
            it.collections,
            it.history,
            it.tags,
            it.appSettings,
        )
    },
    restore = {
        val library = it[0]
        RestoreOptions(
            libraryEntries = library,
            collections = library,
            history = library,
            tags = library,
            appSettings = it[4],
        )
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataStorageScreen(
    viewModel: DataStorageViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var backupOptions by rememberSaveable(stateSaver = BackupOptionsSaver) {
        mutableStateOf(BackupOptions.ALL)
    }
    var restoreOptions by rememberSaveable(stateSaver = RestoreOptionsSaver) {
        mutableStateOf(RestoreOptions.ALL)
    }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportBackup(context, it, backupOptions) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { pendingImportUri = it }
    }

    pendingImportUri?.let { importUri ->
        RestoreOptionsDialog(
            options = restoreOptions,
            onOptionsChange = { restoreOptions = it },
            onConfirm = {
                viewModel.importBackup(context, importUri, restoreOptions)
                pendingImportUri = null
            },
            onDismiss = { pendingImportUri = null },
        )
    }

    LaunchedEffect(state.lastBackupResult) {
        state.lastBackupResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearBackupResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data & Storage") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                BackupSection(
                    isExporting = state.isExporting,
                    isImporting = state.isImporting,
                    backupOptions = backupOptions,
                    onOptionsChange = { backupOptions = it },
                    onExport = { exportLauncher.launch(backupFileName()) },
                    onImport = { importLauncher.launch(arrayOf("application/json")) },
                )
            }

            item {
                AutoBackupSection(
                    state = state,
                    onEnabledChange = viewModel::setAutoBackupEnabled,
                    onFrequencySelected = viewModel::setAutoBackupFrequency,
                    onKeepCountChange = viewModel::setAutoBackupKeepCount,
                )
            }

            item {
                StorageSection(storage = state.storage)
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun BackupSection(
    isExporting: Boolean,
    isImporting: Boolean,
    backupOptions: BackupOptions,
    onOptionsChange: (BackupOptions) -> Unit,
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

            Text("Include in backup", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))

            Text("Library", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            BackupOptionRow(
                label = "Library entries",
                captions = listOf("Games, changelog, and download links"),
                checked = backupOptions.libraryEntries,
                enabled = true,
                onCheckedChange = { checked ->
                    onOptionsChange(
                        if (checked) {
                            // Dependent groups are forced on with Library entries
                            // and cannot be unchecked independently (Mihon-style).
                            backupOptions.copy(
                                libraryEntries = true,
                                collections = true,
                                history = true,
                                tags = true,
                            )
                        } else {
                            backupOptions.copy(
                                libraryEntries = false,
                                collections = false,
                                history = false,
                                tags = false,
                            )
                        }
                    )
                },
            )
            val libraryLockNote = if (backupOptions.libraryEntries) {
                "Included with Library entries"
            } else {
                "Requires Library entries"
            }
            BackupOptionRow(
                label = "Collections",
                captions = listOf("Collections and membership", libraryLockNote),
                checked = backupOptions.libraryEntries,
                enabled = false,
                onCheckedChange = null,
            )
            BackupOptionRow(
                label = "Play history",
                captions = listOf("Play sessions and routes", libraryLockNote),
                checked = backupOptions.libraryEntries,
                enabled = false,
                onCheckedChange = null,
            )
            BackupOptionRow(
                label = "Tags",
                captions = listOf("Tags and game tag assignments", libraryLockNote),
                checked = backupOptions.libraryEntries,
                enabled = false,
                onCheckedChange = null,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Settings", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            BackupOptionRow(
                label = "App settings",
                captions = listOf("Theme, palette, source preferences, and more"),
                checked = backupOptions.appSettings,
                enabled = true,
                onCheckedChange = { checked ->
                    onOptionsChange(
                        if (checked) {
                            backupOptions.copy(appSettings = true)
                        } else {
                            // Private settings depend on app settings.
                            backupOptions.copy(appSettings = false, privateSettings = false)
                        }
                    )
                },
            )
            BackupOptionRow(
                label = "Private settings",
                captions = listOf("Sensitive settings such as saved cookies"),
                checked = backupOptions.privateSettings,
                enabled = backupOptions.appSettings,
                onCheckedChange = { checked -> onOptionsChange(backupOptions.copy(privateSettings = checked)) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting && backupOptions.canCreate(),
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isExporting) "Exporting..." else "Export Backup")
            }
            if (!backupOptions.canCreate()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select at least one group",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
private fun RestoreOptionsDialog(
    options: RestoreOptions,
    onOptionsChange: (RestoreOptions) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore backup") },
        text = {
            Column {
                Text(
                    "Choose which groups to restore from this backup",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text("Library", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                RestoreOptionRow(
                    label = "Library entries",
                    captions = listOf("Games, changelog, and download links"),
                    checked = options.libraryEntries,
                    enabled = true,
                    onCheckedChange = { checked ->
                        // A library restore wipes games and cascades through
                        // collections/history/tags, so dependents travel with
                        // it (mirrors the export lock and backend forcing).
                        onOptionsChange(
                            if (checked) {
                                options.copy(
                                    libraryEntries = true,
                                    collections = true,
                                    history = true,
                                    tags = true,
                                )
                            } else {
                                options.copy(
                                    libraryEntries = false,
                                    collections = false,
                                    history = false,
                                    tags = false,
                                )
                            }
                        )
                    },
                )
                val libraryLockNote = if (options.libraryEntries) {
                    "Restored with Library entries"
                } else {
                    "Requires Library entries"
                }
                RestoreOptionRow(
                    label = "Collections",
                    captions = listOf(libraryLockNote),
                    checked = options.libraryEntries,
                    enabled = false,
                    onCheckedChange = null,
                )
                RestoreOptionRow(
                    label = "Play history",
                    captions = listOf(libraryLockNote),
                    checked = options.libraryEntries,
                    enabled = false,
                    onCheckedChange = null,
                )
                RestoreOptionRow(
                    label = "Tags",
                    captions = listOf(libraryLockNote),
                    checked = options.libraryEntries,
                    enabled = false,
                    onCheckedChange = null,
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Settings", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                RestoreOptionRow(
                    label = "App settings",
                    captions = listOf("Theme, palette, source preferences, and more"),
                    checked = options.appSettings,
                    enabled = true,
                    onCheckedChange = { checked -> onOptionsChange(options.copy(appSettings = checked)) },
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = options.canRestore()) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun RestoreOptionRow(
    label: String,
    captions: List<String> = emptyList(),
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            captions.forEach { caption ->
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BackupOptionRow(
    label: String,
    captions: List<String> = emptyList(),
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            captions.forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AutoBackupSection(
    state: DataStorageUiState,
    onEnabledChange: (Boolean) -> Unit,
    onFrequencySelected: (AutoBackupFrequency) -> Unit,
    onKeepCountChange: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Automatic backup", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Export a full backup on a schedule",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Enable automatic backup",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.autoBackupEnabled, onCheckedChange = onEnabledChange)
            }

            if (state.autoBackupEnabled) {
                Spacer(modifier = Modifier.height(8.dp))

                Text("Frequency", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Column(modifier = Modifier.selectableGroup()) {
                    AutoBackupFrequency.entries.forEach { frequency ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .selectable(
                                    selected = state.autoBackupFrequency == frequency,
                                    onClick = { onFrequencySelected(frequency) },
                                    role = Role.RadioButton,
                                )
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.autoBackupFrequency == frequency,
                                onClick = null,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = frequency.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Backups to keep", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onKeepCountChange(state.autoBackupKeepCount - 1) },
                        enabled = state.autoBackupKeepCount > MIN_KEEP_COUNT,
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Fewer backups")
                    }
                    Text(
                        text = "${state.autoBackupKeepCount}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    IconButton(
                        onClick = { onKeepCountChange(state.autoBackupKeepCount + 1) },
                        enabled = state.autoBackupKeepCount < MAX_KEEP_COUNT,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "More backups")
                    }
                }
                Text(
                    text = "Older automatic backups beyond this count are deleted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Folder: ${state.autoBackupDirPath ?: "unavailable"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Automatic backups live in app storage and are lost if the app is uninstalled. Use Export Backup to save copies outside the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun backupFileName(): String {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
    return "gamevault_$timestamp.json"
}

@Composable
private fun StorageSection(storage: StorageStats?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Storage", style = MaterialTheme.typography.titleSmall)

            if (storage == null) {
                Text(
                    text = "Calculating...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Used: ${formatGb(storage.usedBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Available: ${formatGb(storage.availableBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Total: ${formatGb(storage.totalBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (storage.totalBytes > 0) storage.usedBytes.toFloat() / storage.totalBytes else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
    }
}

private fun formatGb(bytes: Long): String =
    String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)