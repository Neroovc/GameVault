package com.gamevault.app.ui.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamevault.app.data.settings.AppSettings
import com.gamevault.app.domain.source.GameSource
import com.gamevault.app.domain.source.SourceManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Extensions screen — lists all registered game sources with their enabled
 * state and per-source options, analogous to Mihon's extension list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    sourceManager: SourceManager,
    appSettings: AppSettings,
    onSourceClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val sources = remember { sourceManager.sources }
    var optionsSource by remember { mutableStateOf<GameSource?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            items(sources, key = { it.id }) { source ->
                var enabled by remember(source.id) { mutableStateOf(true) }
                // The flow emits the current value immediately, so this single
                // collect covers both the initial state and later updates.
                LaunchedEffect(source.id) {
                    sourceManager.isEnabled(source.id).collect { enabled = it }
                }

                SourceRow(
                    source = source,
                    enabled = enabled,
                    onEnabledChange = { value ->
                        scope.launch { sourceManager.setEnabled(source.id, value) }
                    },
                    onOptionsClick = { optionsSource = source },
                    onClick = { onSourceClick(source.id) },
                )
            }
        }
    }

    val dialogSource = optionsSource
    if (dialogSource != null) {
        var cookie by remember(dialogSource.id) { mutableStateOf("") }
        LaunchedEffect(dialogSource.id) {
            cookie = appSettings.f95zoneCookie.first() ?: ""
        }

        val isF95Zone = dialogSource.id == "f95zone"
        AlertDialog(
            onDismissRequest = { optionsSource = null },
            title = { Text("${dialogSource.name} options") },
            text = {
                if (isF95Zone) {
                    Column {
                        OutlinedTextField(
                            value = cookie,
                            onValueChange = { cookie = it },
                            label = { Text("Session cookie (optional)") },
                            supportingText = {
                                Text("Paste xf_session cookie to fetch member-only content")
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Text("No configurable options for this source.")
                }
            },
            confirmButton = {
                if (isF95Zone) {
                    TextButton(
                        onClick = {
                            scope.launch { appSettings.setF95zoneCookie(cookie.ifBlank { null }) }
                            optionsSource = null
                        },
                    ) { Text("Save") }
                } else {
                    TextButton(onClick = { optionsSource = null }) { Text("Close") }
                }
            },
            dismissButton = {
                if (isF95Zone) {
                    TextButton(onClick = { optionsSource = null }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun SourceRow(
    source: GameSource,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onOptionsClick: () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(source.name) },
        supportingContent = {
            Text(
                text = source.description,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            // Real source logos — do NOT tint.
            Icon(
                painter = painterResource(source.iconRes),
                contentDescription = source.name,
                modifier = Modifier.size(32.dp),
                tint = Color.Unspecified,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOptionsClick) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Options",
                        modifier = Modifier.size(18.dp),
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
