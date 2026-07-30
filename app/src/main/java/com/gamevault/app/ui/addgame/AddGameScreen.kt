package com.gamevault.app.ui.addgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gamevault.app.domain.model.GameEngine
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.domain.model.SourceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGameScreen(
    viewModel: AddGameViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    // Navigate back on save success
    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Game") },
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Tabs
            TabRow(selectedTabIndex = state.activeTab) {
                Tab(
                    selected = state.activeTab == 0,
                    onClick = { viewModel.setActiveTab(0) },
                    text = { Text("From URL") },
                )
                Tab(
                    selected = state.activeTab == 1,
                    onClick = { viewModel.setActiveTab(1) },
                    text = { Text("Manual") },
                )
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.activeTab == 0) {
                    // URL import section
                    UrlImportSection(
                        url = state.url,
                        isScraping = state.isScraping,
                        scrapeError = state.scrapeError,
                        onUrlChanged = viewModel::setUrl,
                        onFetch = viewModel::fetchFromUrl,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ── Form fields (shared by both tabs) ──

                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::updateTitle,
                    label = { Text("Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.coverUrl,
                    onValueChange = viewModel::updateCoverUrl,
                    label = { Text("Cover URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.developer,
                        onValueChange = viewModel::updateDeveloper,
                        label = { Text("Developer") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.version,
                        onValueChange = viewModel::updateVersion,
                        label = { Text("Version") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Engine dropdown
                EnumDropdown(
                    label = "Engine",
                    selected = state.engine,
                    values = GameEngine.entries.toTypedArray(),
                    onSelected = viewModel::updateEngine,
                    displayName = { it.displayName },
                )

                // Status dropdown
                EnumDropdown(
                    label = "Status",
                    selected = state.status,
                    values = GameStatus.entries.toTypedArray(),
                    onSelected = viewModel::updateStatus,
                    displayName = { it.displayName },
                )

                // Source type dropdown
                EnumDropdown(
                    label = "Source",
                    selected = state.sourceType,
                    values = SourceType.entries.toTypedArray(),
                    onSelected = viewModel::updateSourceType,
                    displayName = { it.displayName },
                )

                OutlinedTextField(
                    value = state.sourceUrl,
                    onValueChange = viewModel::updateSourceUrl,
                    label = { Text("Source URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::updateDescription,
                    label = { Text("Description") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Save button
                Button(
                    onClick = viewModel::saveGame,
                    enabled = state.title.isNotBlank() && !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Save Game")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun UrlImportSection(
    url: String,
    isScraping: Boolean,
    scrapeError: String?,
    onUrlChanged: (String) -> Unit,
    onFetch: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Import from F95Zone",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChanged,
                label = { Text("F95Zone URL") },
                placeholder = { Text("https://f95zone.to/threads/...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onFetch,
                enabled = url.isNotBlank() && !isScraping,
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Fetch")
            }
        }

        if (isScraping) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (scrapeError != null) {
            Text(
                text = scrapeError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Reusable Material3 ExposedDropdownMenuBox for enum selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> EnumDropdown(
    label: String,
    selected: T?,
    values: Array<T>,
    onSelected: (T) -> Unit,
    displayName: (T) -> String,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected?.let(displayName) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(displayName(value)) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
}
