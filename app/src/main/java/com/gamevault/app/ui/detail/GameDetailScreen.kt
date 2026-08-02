package com.gamevault.app.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gamevault.app.domain.model.Collection
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameRoute
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.domain.model.RouteStatus
import com.gamevault.app.domain.model.SourceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    viewModel: GameDetailViewModel?,
    onBack: () -> Unit,
    onDeleted: (() -> Unit)? = null,
    previewGame: Game? = null,
    sourceName: String? = null,
    addingToLibrary: Boolean = false,
    allCollections: List<Collection> = emptyList(),
    initialCollectionIds: List<Long> = emptyList(),
    onAddToLibrary: ((List<Long>) -> Unit)? = null,
) {
    // Browse preview mode: same screen chrome, only preview-relevant sections
    // (header, quick actions, About, genres). No viewModel — the game is
    // not saved yet.
    if (previewGame != null) {
        GameDetailPreviewContent(
            game = previewGame,
            sourceName = sourceName ?: "",
            addingToLibrary = addingToLibrary,
            allCollections = allCollections,
            initialCollectionIds = initialCollectionIds,
            onAddToLibrary = onAddToLibrary,
            onBack = onBack,
        )
        return
    }

    // Saved-game mode: the caller must provide a viewModel.
    val uiState by viewModel!!.uiState.collectAsState()
    var showTimeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.game?.title ?: "Game") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::showEditNotes) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit notes")
                    }
                    IconButton(onClick = viewModel::showDeleteConfirm) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete game",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading || uiState.game == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (uiState.isLoading) "Loading..." else "Game not found")
            }
        } else {
            val game = uiState.game!!

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Cover + basic info
                item {
                    GameHeader(
                        game = game,
                        onRatingChange = viewModel::updatePersonalRating,
                    )
                }

                // Quick actions: library status, manual play time, soon, web
                item {
                    DetailActionRow(
                        game = game,
                        isSaved = true,
                        addingToLibrary = false,
                        onAddToLibrary = viewModel::showCollectionPicker,
                        onAdjustTime = { showTimeDialog = true },
                    )
                }

                // Description (Mihon parity — was missing from the detail screen)
                item {
                    AboutSection(game = game)
                }

                // Scraped genre tags
                if (game.tags.isNotEmpty()) {
                    item {
                        GenresRow(tags = game.tags)
                    }
                }

                // Status selector
                item {
                    StatusSelector(
                        current = game.status,
                        onSelected = viewModel::updateGameStatus,
                    )
                }

                // Play session controls
                item {
                    PlaySessionControls(
                        isPlaying = uiState.sessions.any { it.endTime == null },
                        onStart = viewModel::startPlaySession,
                        onStop = {
                            val activeSession = uiState.sessions.find { it.endTime == null }
                            activeSession?.let { viewModel.endPlaySession(it.id) }
                        },
                        totalPlayTime = uiState.totalPlayTime,
                    )
                }

                // Collections section
                item {
                    CollectionsSection(
                        gameCollections = uiState.gameCollections,
                        onAddToCollection = viewModel::showCollectionPicker,
                    )
                }

                // Routes section
                item {
                    RoutesSectionHeader(onAddRoute = { name -> viewModel.addRoute(name) })
                }

                items(uiState.routes, key = { it.id }) { route ->
                    RouteItem(
                        route = route,
                        onProgressChange = { viewModel.updateRouteProgress(route.id, it) },
                        onDelete = { viewModel.deleteRoute(route) },
                    )
                }

                // Notes (read-only display + edit trigger)
                item {
                    NotesSection(
                        notes = game.notes,
                        onEdit = viewModel::showEditNotes,
                    )
                }

                // Bottom spacer
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Collection picker dialog
    if (uiState.showCollectionPicker) {
        val gameCollectionIds = uiState.gameCollections.map { it.id }.toSet()
        AlertDialog(
            onDismissRequest = viewModel::dismissCollectionPicker,
            title = { Text("Add to Collection") },
            text = {
                Column {
                    if (uiState.allCollections.isEmpty()) {
                        Text(
                            "No collections yet. Create one in Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        uiState.allCollections.forEach { collection ->
                            val isInCollection = collection.id in gameCollectionIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isInCollection,
                                    onCheckedChange = {
                                        viewModel.toggleGameCollection(collection.id, isInCollection)
                                    },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = collection.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissCollectionPicker) {
                    Text("Done")
                }
            },
        )
    }

    // Manual play time dialog
    if (showTimeDialog) {
        ManualTimeDialog(
            currentMinutes = uiState.game?.playTimeMinutes ?: 0L,
            onConfirm = { minutes ->
                showTimeDialog = false
                viewModel.setManualPlayTime(minutes)
            },
            onDismiss = { showTimeDialog = false },
        )
    }

    // Delete confirmation dialog
    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            title = { Text("Delete Game") },
            text = {
                Text("Are you sure you want to delete \"${uiState.game?.title ?: ""}\"? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGame()
                        onDeleted?.invoke()
                        onBack()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirm) {
                    Text("Cancel")
                }
            },
        )
    }

    // Edit notes dialog
    if (uiState.showEditNotes) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEditNotes,
            title = { Text("Edit Notes") },
            text = {
                OutlinedTextField(
                    value = uiState.editNotesText,
                    onValueChange = viewModel::setEditNotesText,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.updateNotes(uiState.editNotesText) }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEditNotes) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun statusMetaIcon(status: GameStatus): ImageVector = when (status) {
    GameStatus.NOT_STARTED -> Icons.Filled.Circle
    GameStatus.PLAYING -> Icons.Filled.PlayArrow
    GameStatus.COMPLETED -> Icons.Filled.Check
    GameStatus.REPLAYING -> Icons.Filled.Repeat
    GameStatus.PAUSED -> Icons.Filled.Pause
    GameStatus.ABANDONED -> Icons.Filled.Flag
}

@Composable
private fun GameHeader(
    game: com.gamevault.app.domain.model.Game,
    onRatingChange: ((Float) -> Unit)? = null,
    sourceName: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(3f / 4f),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (game.coverUrl != null) {
                AsyncImage(
                    model = game.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleLarge,
            )

            if (game.developer != null) {
                Text(
                    text = game.developer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Status + source meta row (source skipped for MANUAL in saved mode)
            val sourceLabel = when {
                sourceName != null -> sourceName
                game.sourceType != SourceType.MANUAL -> game.sourceType.displayName
                else -> null
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = statusMetaIcon(game.status),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = game.status.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sourceLabel != null) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (game.engine != null) {
                Text(
                    text = game.engine.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            if (onRatingChange != null) {
                Spacer(modifier = Modifier.height(8.dp))

                RatingBar(
                    rating = game.personalRating ?: 0f,
                    onRatingChange = onRatingChange,
                )
            }
        }
    }
}

@Composable
private fun RatingBar(
    rating: Float,
    onRatingChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { star ->
            val filled = rating >= star
            IconButton(onClick = {
                val newRating = if (rating == star.toFloat()) star - 0.5f else star.toFloat()
                onRatingChange(newRating.coerceIn(0.5f, 5.0f))
            }) {
                Icon(
                    imageVector = if (filled) Icons.Filled.Star
                    else if (rating >= star - 0.5f) Icons.Filled.Star
                    else Icons.Outlined.StarBorder,
                    contentDescription = "Star $star",
                    tint = if (filled || rating >= star - 0.5f)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text = if (rating > 0) String.format("%.1f", rating) else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusSelector(
    current: GameStatus,
    onSelected: (GameStatus) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Status", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                GameStatus.entries.forEach { status ->
                    FilledTonalButton(
                        onClick = { onSelected(status) },
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(
                            status.displayName,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaySessionControls(
    isPlaying: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    totalPlayTime: Long,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Play Time", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = formatPlayTime(totalPlayTime),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Button(
                onClick = if (isPlaying) onStop else onStart,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isPlaying) "Stop" else "Start Playing")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionsSection(
    gameCollections: List<com.gamevault.app.domain.model.Collection>,
    onAddToCollection: () -> Unit,
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
                FilledTonalButton(onClick = onAddToCollection) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (gameCollections.isEmpty()) {
                Text(
                    text = "Not in any collection",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    gameCollections.forEach { collection ->
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(collection.name, style = MaterialTheme.typography.labelSmall)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Bookmark,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutesSectionHeader(
    onAddRoute: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Routes / Endings", style = MaterialTheme.typography.titleSmall)
        FilledTonalButton(onClick = { onAddRoute("New Route") }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add Route", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RouteItem(
    route: GameRoute,
    onProgressChange: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (route.status) {
                RouteStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = route.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "${route.progress}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { route.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = route.status.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotesSection(
    notes: String?,
    onEdit: () -> Unit,
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
                Text("Notes", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit notes", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (notes != null) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "No notes yet. Tap edit to add notes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

private fun formatPlayTime(minutes: Long): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDetailPreviewContent(
    game: com.gamevault.app.domain.model.Game,
    sourceName: String,
    addingToLibrary: Boolean,
    allCollections: List<Collection>,
    initialCollectionIds: List<Long>,
    onAddToLibrary: ((List<Long>) -> Unit)?,
    onBack: () -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var checkedIds by remember { mutableStateOf(initialCollectionIds.toSet()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(game.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header: cover + title/developer/engine/source (no rating).
            item {
                GameHeader(game = game, sourceName = sourceName)
            }

            // Quick actions: add to library, play time (disabled), soon (disabled), web
            item {
                DetailActionRow(
                    game = game,
                    isSaved = false,
                    addingToLibrary = addingToLibrary,
                    onAddToLibrary = {
                        if (allCollections.isEmpty()) {
                            onAddToLibrary?.invoke(emptyList())
                        } else {
                            pickerOpen = true
                        }
                    },
                    onAdjustTime = null,
                )
            }

            // Description
            item {
                AboutSection(game = game)
            }

            // Scraped genre tags
            if (game.tags.isNotEmpty()) {
                item {
                    GenresRow(tags = game.tags)
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Collection picker for the add-to-library flow
    if (pickerOpen) {
        PreviewCollectionPicker(
            collections = allCollections,
            checkedIds = checkedIds,
            onCheckedChange = { id, checked ->
                checkedIds = if (checked) checkedIds + id else checkedIds - id
            },
            onConfirm = {
                pickerOpen = false
                onAddToLibrary?.invoke(checkedIds.toList())
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun AboutSection(game: com.gamevault.app.domain.model.Game) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .animateContentSize()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("About", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (expanded) "Show less" else "Show more",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = game.description ?: "No description available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailActionRow(
    game: com.gamevault.app.domain.model.Game,
    isSaved: Boolean,
    addingToLibrary: Boolean,
    onAddToLibrary: () -> Unit,
    onAdjustTime: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ActionButton(
            icon = {
                Icon(
                    if (isSaved) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                )
            },
            label = if (isSaved) "In library" else if (addingToLibrary) "Adding..." else "Add to library",
            enabled = if (isSaved) true else !addingToLibrary,
            onClick = onAddToLibrary,
        )
        ActionButton(
            icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
            label = "Play time",
            enabled = onAdjustTime != null,
            onClick = { onAdjustTime?.invoke() },
        )
        ActionButton(
            icon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
            label = "Soon",
            enabled = false,
            onClick = {},
        )
        val url = game.f95Url ?: game.sourceUrl
        val context = LocalContext.current
        ActionButton(
            icon = { Icon(Icons.Default.Public, contentDescription = null) },
            label = "Web",
            enabled = url != null,
            onClick = {
                if (url != null) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
        )
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(44.dp),
        ) {
            icon()
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun GenresRow(tags: List<com.gamevault.app.domain.model.Tag>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tags, key = { it.name }) { tag ->
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ManualTimeDialog(
    currentMinutes: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var hoursText by remember { mutableStateOf((currentMinutes / 60).toString()) }
    var minutesText by remember { mutableStateOf((currentMinutes % 60).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust play time") },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { input -> hoursText = input.filter { it.isDigit() }.take(3) },
                    label = { Text("Hours") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Text("h")
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { input -> minutesText = input.filter { it.isDigit() }.take(2) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Text("min")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hours = hoursText.toIntOrNull() ?: 0
                val mins = minutesText.toIntOrNull() ?: 0
                onConfirm(hours * 60L + mins)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PreviewCollectionPicker(
    collections: List<com.gamevault.app.domain.model.Collection>,
    checkedIds: Set<Long>,
    onCheckedChange: (Long, Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to library") },
        text = {
            Column {
                if (collections.isEmpty()) {
                    Text("No collections yet. The game will be added to your library.")
                } else {
                    collections.forEach { collection ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCheckedChange(collection.id, collection.id !in checkedIds) },
                        ) {
                            Checkbox(
                                checked = collection.id in checkedIds,
                                onCheckedChange = { onCheckedChange(collection.id, it) },
                            )
                            Text(collection.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
