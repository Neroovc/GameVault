package com.gamevault.app.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.gamevault.app.data.local.downloadCoverToDevice
import com.gamevault.app.data.local.savePickedImage
import com.gamevault.app.domain.model.Collection
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameRoute
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.domain.model.RouteStatus
import com.gamevault.app.domain.model.SourceType
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showTimeDialog by remember { mutableStateOf(false) }
    var showTimeMenu by remember { mutableStateOf(false) }
    var coverViewerOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                            CircleShape,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                CircleShape,
                            ),
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Notes") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.showEditNotes()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Edit collections") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.showCollectionPicker()
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            var aboutExpanded by remember { mutableStateOf(false) }

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
                        onCoverClick = { coverViewerOpen = true },
                        onStatusChange = viewModel::updateGameStatus,
                    )
                }

                // Quick actions: library status, manual play time, soon, web
                item {
                    DetailActionRow(
                        game = game,
                        isSaved = game.inLibrary,
                        addingToLibrary = false,
                        onAddToLibrary = { viewModel.addToLibrary() },
                        onRemoveFromLibrary = { viewModel.removeFromLibrary() },
                        onPickCollection = viewModel::showCollectionPicker,
                        onAdjustTime = { showTimeMenu = true },
                        playTimeMinutes = uiState.game?.playTimeMinutes ?: 0L,
                    )
                }

                // Notes — visible above the description, separated by a divider
                if (!game.notes.isNullOrBlank()) {
                    item {
                        NotesSection(
                            notes = game.notes,
                            showEditButton = aboutExpanded,
                            onEdit = viewModel::showEditNotes,
                        )
                    }
                    item {
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }

                // Description, plain text (no background container)
                item {
                    AboutSection(
                        game = game,
                        expanded = aboutExpanded,
                        onToggle = { aboutExpanded = !aboutExpanded },
                    )
                }

                // Scraped genre tags
                if (game.tags.isNotEmpty()) {
                    item {
                        GenresRow(tags = game.tags)
                    }
                }

                // Details: developer, engine, version, changelog, links
                item {
                    DetailsSection(game = game)
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

    // Play time menu: entry point for the per-game time actions (adjust, and
    // upcoming options). Shows the game's current total so the label is useful.
    if (showTimeMenu) {
        PlayTimeMenuDialog(
            currentPlayTime = uiState.game?.playTimeMinutes ?: 0L,
            onAdjustTime = {
                showTimeMenu = false
                showTimeDialog = true
            },
            onComingSoon = { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            },
            onDismiss = { showTimeMenu = false },
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

    // Fullscreen cover viewer
    if (coverViewerOpen) {
        uiState.game?.let { game ->
            CoverViewerDialog(
                cover = game.localCoverPath ?: game.coverUrl,
                gameId = game.id,
                gameTitle = game.title,
                canCustomize = true,
                onSetCustomCover = viewModel::setLocalCover,
                onShowMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                onDismiss = { coverViewerOpen = false },
            )
        }
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
    onCoverClick: (() -> Unit)? = null,
    onStatusChange: ((GameStatus) -> Unit)? = null,
) {
    var statusMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(3f / 4f)
                .clickable(
                    enabled = onCoverClick != null &&
                        (game.coverUrl != null || game.localCoverPath != null),
                    onClick = { onCoverClick?.invoke() },
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (game.coverUrl != null || game.localCoverPath != null) {
                AsyncImage(
                    model = game.localCoverPath ?: game.coverUrl,
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
                if (onStatusChange != null) {
                    Box {
                        FilledTonalButton(
                            onClick = { statusMenuExpanded = true },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        ) {
                            Icon(
                                imageVector = statusMetaIcon(game.status),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = game.status.displayName,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = statusMenuExpanded,
                            onDismissRequest = { statusMenuExpanded = false },
                        ) {
                            GameStatus.entries.forEach { status ->
                                DropdownMenuItem(
                                    text = { Text(status.displayName) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = statusMetaIcon(status),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        if (status == game.status) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        statusMenuExpanded = false
                                        onStatusChange(status)
                                    },
                                )
                            }
                        }
                    }
                } else {
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
                }
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
@OptIn(ExperimentalMaterial3Api::class)
private fun RatingBar(
    rating: Float,
    onRatingChange: (Float) -> Unit,
) {
    val sliderValue = rating.coerceIn(0.5f, 5.0f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            (1..5).forEach { star ->
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            val newRating = if (rating == star.toFloat()) star - 0.5f else star.toFloat()
                            onRatingChange(newRating.coerceIn(0.5f, 5.0f))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (rating >= star - 0.5f) Icons.Filled.Star
                        else Icons.Outlined.StarBorder,
                        contentDescription = "Star $star",
                        tint = if (rating >= star - 0.5f)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = if (rating > 0) String.format("%.1f", rating) else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = onRatingChange,
            valueRange = 0.5f..5.0f,
            steps = 44,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            // Compact thumb: shrink the default 20dp circle to 12dp; the
            // default track is kept (custom track needs SliderState internals).
            thumb = {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            },
        )
    }
}

@Composable
private fun PlayTimeMenuDialog(
    currentPlayTime: Long,
    onAdjustTime: () -> Unit,
    onComingSoon: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play time") },
        text = {
            Column {
                Text(
                    text = formatPlayTime(currentPlayTime),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = onAdjustTime,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Adjust play time")
                }
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { onComingSoon("Coming soon") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Coming soon")
                }
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { onComingSoon("Coming soon") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Coming soon")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

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
    var aboutExpanded by remember { mutableStateOf(false) }
    var coverViewerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                            CircleShape,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // Header: cover + title/developer/source (no rating).
            item {
                GameHeader(
                    game = game,
                    sourceName = sourceName,
                    onCoverClick = { coverViewerOpen = true },
                )
            }

            // Quick actions: add to library, play time (disabled), soon (disabled), web
            item {
                DetailActionRow(
                    game = game,
                    isSaved = game.inLibrary,
                    addingToLibrary = addingToLibrary,
                    onAddToLibrary = {
                        if (allCollections.isEmpty()) {
                            onAddToLibrary?.invoke(emptyList())
                        } else {
                            pickerOpen = true
                        }
                    },
                    onRemoveFromLibrary = null,
                    onPickCollection = null,
                    onAdjustTime = null,
                )
            }

            // Description (state hoisted to the composable body so it survives scrolls)
            item {
                AboutSection(
                    game = game,
                    expanded = aboutExpanded,
                    onToggle = { aboutExpanded = !aboutExpanded },
                )
            }

            // Scraped genre tags
            if (game.tags.isNotEmpty()) {
                item {
                    GenresRow(tags = game.tags)
                }
            }

            // Details: developer, engine, version, changelog, links
            item {
                DetailsSection(game = game)
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

    // Fullscreen cover viewer — preview games aren't saved yet, so the custom
    // photo action is not offered; only Download and Exit apply.
    if (coverViewerOpen) {
        CoverViewerDialog(
            cover = game.coverUrl,
            gameId = game.id,
            gameTitle = game.title,
            canCustomize = false,
            onSetCustomCover = {},
            onShowMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            onDismiss = { coverViewerOpen = false },
        )
    }
}

@Composable
private fun NotesSection(
    notes: String?,
    showEditButton: Boolean,
    onEdit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Notes", style = MaterialTheme.typography.titleSmall)
            // Edit note appears only when the description below is expanded,
            // keeping the collapsed view clean.
            if (showEditButton) {
                TextButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Edit note", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = notes.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutSection(
    game: com.gamevault.app.domain.model.Game,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    // Plain text, no background container (Mihon parity)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .animateContentSize(),
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

@Composable
private fun DetailActionRow(
    game: com.gamevault.app.domain.model.Game,
    isSaved: Boolean,
    addingToLibrary: Boolean,
    onAddToLibrary: () -> Unit = {},
    onRemoveFromLibrary: (() -> Unit)? = null,
    onPickCollection: (() -> Unit)? = null,
    onAdjustTime: (() -> Unit)?,
    playTimeMinutes: Long = 0,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ActionButton(
            icon = {
                Icon(
                    if (isSaved) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = if (isSaved)
                        "Remove from library (tap) / Choose collection (long press)"
                    else null,
                )
            },
            label = if (isSaved) "In library" else if (addingToLibrary) "Adding..." else "Add to library",
            enabled = if (isSaved) true else !addingToLibrary,
            onClick = if (isSaved) (onRemoveFromLibrary ?: {}) else onAddToLibrary,
            onLongClick = if (isSaved) onPickCollection else null,
        )
        ActionButton(
            icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
            label = if (playTimeMinutes > 0) formatPlayTime(playTimeMinutes) else "Play time",
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
private fun CoverViewerDialog(
    cover: String?,
    gameId: Long,
    gameTitle: String,
    canCustomize: Boolean,
    onSetCustomCover: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }

    // System photo picker — no storage permissions needed (API 19+).
    // activity-compose 1.9.3 ships PickVisualMedia, so no legacy fallback.
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val path = savePickedImage(context, uri, gameId)
                if (path != null) {
                    onSetCustomCover(path)
                    onDismiss()
                } else {
                    onShowMessage("Failed to save custom cover")
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Full-screen black viewer. The whole Box is clickable so tapping
        // anywhere (image included) dismisses.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
        ) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )

            // Bottom action bar. Surface(onClick = {}) consumes taps on the bar
            // itself so they don't fall through to the dismiss layer.
            Surface(
                onClick = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ActionButton(
                        icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        label = "Download",
                        enabled = cover != null && !downloading,
                        onClick = {
                            if (cover != null) {
                                downloading = true
                                scope.launch {
                                    val message = downloadCoverToDevice(context, cover, gameTitle)
                                    downloading = false
                                    onShowMessage(message)
                                }
                            }
                        },
                    )
                    if (canCustomize) {
                        ActionButton(
                            icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                            label = "Custom photo",
                            enabled = true,
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                        )
                    }
                    ActionButton(
                        icon = { Icon(Icons.Filled.Close, contentDescription = null) },
                        label = "Exit",
                        enabled = true,
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier
                .size(44.dp)
                .combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            shape = CircleShape,
            color = if (enabled)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CompositionLocalProvider(
                    LocalContentColor provides
                        (if (enabled)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)),
                ) {
                    icon()
                }
            }
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

/**
 * Key/value metadata card shown below the description and genres: developer,
 * engine, version, changelog and developer links.
 * Rows are omitted entirely when the value is missing.
 */
@Composable
private fun DetailsSection(
    game: com.gamevault.app.domain.model.Game,
) {
    val context = LocalContext.current
    val devLinks = game.devLinks.take(4)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Details", style = MaterialTheme.typography.titleSmall)

            if (!game.developer.isNullOrBlank()) {
                DetailsRow(icon = Icons.Filled.Person, label = "Developer", value = game.developer)
            }
            if (game.engine != null) {
                DetailsRow(icon = Icons.Filled.Build, label = "Engine", value = game.engine.displayName)
            }
            if (!game.version.isNullOrBlank()) {
                DetailsRow(icon = Icons.Filled.Flag, label = "Version", value = game.version)
            }
            if (!game.changelog.isNullOrBlank()) {
                DetailsRow(
                    icon = Icons.Filled.History,
                    label = "Changelog",
                    value = game.changelog,
                    maxLines = 4,
                )
            }

            if (devLinks.isNotEmpty()) {
                DetailsDivider()
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Developer links",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    devLinks.forEachIndexed { index, link ->
                        if (index > 0) DetailsDivider()
                        val (icon, label) = devLinkPlatform(link)
                        DetailsLinkRow(
                            icon = icon,
                            label = label,
                            value = link,
                            onOpen = { openUrl(context, link) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Maps a developer URL to a platform icon + label, e.g.
 * "https://t.me/xxx" -> (Send, "Telegram"). Unknown hosts fall back to a
 * Link icon with the bare host as the label.
 */
private fun devLinkPlatform(url: String): Pair<ImageVector, String> {
    val lower = url.lowercase()
    return when {
        "telegram" in lower || "t.me" in lower ->
            Icons.AutoMirrored.Filled.Send to "Telegram"
        "discord" in lower ->
            Icons.Filled.Forum to "Discord"
        "youtube" in lower || "youtu.be" in lower ->
            Icons.Filled.PlayArrow to "YouTube"
        "patreon" in lower ->
            Icons.Filled.Paid to "Patreon"
        "x.com" in lower || "twitter" in lower ->
            Icons.Filled.Star to "X"
        "reddit" in lower ->
            Icons.Filled.Forum to "Reddit"
        "steam" in lower || "steampowered" in lower ->
            Icons.Filled.SportsEsports to "Steam"
        else ->
            Icons.Filled.Link to devLinkHostLabel(lower)
    }
}

private fun devLinkHostLabel(url: String): String {
    val host = url.trim()
        .removePrefix("https://").removePrefix("http://")
        .removePrefix("www.")
        .substringBefore('/')
    return host.takeIf { it.isNotBlank() } ?: "Website"
}

@Composable
private fun DetailsRow(
    icon: ImageVector,
    label: String,
    value: String,
    maxLines: Int = Int.MAX_VALUE,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailsLinkRow(
    icon: ImageVector,
    label: String,
    value: String,
    onOpen: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onOpen) {
                Text("Open", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DetailsDivider() {
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Opens [url] in the system browser, mirroring the "Web" action button. */
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        // No browser activity available — ignore
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
