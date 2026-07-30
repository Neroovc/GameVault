package com.gamevault.app.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamevault.app.domain.model.Collection
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.ui.components.GameCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onGameClick: (Long) -> Unit,
    onAddGame: () -> Unit,
    onCollectionsClick: () -> Unit = {},
    selectionViewModel: LibrarySelectionViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val selectedIds by selectionViewModel.selectedGameIds.collectAsState()
    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsState()
    val selectedCount by selectionViewModel.selectedCount.collectAsState()
    var searchExpanded by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showBulkStatusDialog by remember { mutableStateOf(false) }
    var showBulkCollectionDialog by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (isSelectionMode) {
                SelectionTopAppBar(
                    selectedCount = selectedCount,
                    onClearSelection = selectionViewModel::clearSelection,
                )
            } else {
                TopAppBar(
                    title = { Text("GameVault") },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = { searchExpanded = !searchExpanded }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = if (showFilters ||
                                    uiState.selectedStatus != GameStatusFilter.ALL ||
                                    uiState.selectedCollectionId != null
                                ) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.SwapVert, contentDescription = "Sort")
                            }
                            SortDropdownMenu(
                                expanded = showSortMenu,
                                onDismiss = { showSortMenu = false },
                                currentSort = uiState.sortOrder,
                                onSortSelected = { order ->
                                    viewModel.onSortOrderChanged(order)
                                    showSortMenu = false
                                },
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(onClick = onAddGame) {
                    Icon(Icons.Default.Add, contentDescription = "Add Game")
                }
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                SelectionBottomBar(
                    onStatusChange = { showBulkStatusDialog = true },
                    onAddToCollection = { showBulkCollectionDialog = true },
                    onDelete = { showBulkDeleteDialog = true },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (!isSelectionMode) {
                // Search bar
                AnimatedVisibility(
                    visible = searchExpanded,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    SearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = uiState.searchQuery,
                                onQueryChange = viewModel::onSearchQueryChanged,
                                onSearch = { },
                                expanded = false,
                                onExpandedChange = { },
                                placeholder = { Text("Search games...") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                            )
                        },
                        expanded = false,
                        onExpandedChange = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { }
                }

                // Filter chips (collapsible)
                AnimatedVisibility(
                    visible = showFilters,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        CollectionFilterRow(
                            collections = collections,
                            selectedCollectionId = uiState.selectedCollectionId,
                            onSelected = viewModel::onCollectionFilterChanged,
                        )
                        StatusFilterRow(
                            selected = uiState.selectedStatus,
                            onSelected = {
                                viewModel.onStatusFilterChanged(it)
                                // Keep filters open so user can chain selections
                            },
                        )
                    }
                }
            }

            // Content
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Loading...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (uiState.games.isEmpty()) {
                EmptyLibrary(
                    onAddGame = onAddGame,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = { },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = uiState.games,
                            key = { it.id },
                        ) { game ->
                            GameCard(
                                game = game,
                                isSelected = game.id in selectedIds,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectionViewModel.toggleSelection(game.id)
                                    } else {
                                        onGameClick(game.id)
                                    }
                                },
                                onLongClick = {
                                    selectionViewModel.toggleSelection(game.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showBulkStatusDialog) {
        BulkStatusDialog(
            selectedCount = selectedCount,
            onDismiss = { showBulkStatusDialog = false },
            onStatusSelected = { status ->
                viewModel.updateGameStatusBulk(selectedIds.toList(), status)
                selectionViewModel.clearSelection()
                showBulkStatusDialog = false
            },
        )
    }
    if (showBulkCollectionDialog) {
        BulkCollectionDialog(
            collections = collections,
            selectedCount = selectedCount,
            onDismiss = { showBulkCollectionDialog = false },
            onCollectionSelected = { collectionId ->
                viewModel.addGamesToCollection(selectedIds.toList(), collectionId)
                selectionViewModel.clearSelection()
                showBulkCollectionDialog = false
            },
        )
    }
    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Delete ${selectedCount} games?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGames(selectedIds.toList())
                        selectionViewModel.clearSelection()
                        showBulkDeleteDialog = false
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ── Selection UI ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopAppBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
) {
    TopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = "Clear selection")
            }
        },
    )
}

@Composable
private fun SelectionBottomBar(
    onStatusChange: () -> Unit,
    onAddToCollection: () -> Unit,
    onDelete: () -> Unit,
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(onClick = onStatusChange) { Text("Status") }
            Button(onClick = onAddToCollection) {
                Icon(Icons.Default.CollectionsBookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Collection")
            }
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delete")
            }
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────

@Composable
private fun BulkStatusDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onStatusSelected: (GameStatus) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change status of $selectedCount games to:") },
        text = {
            Column {
                GameStatus.entries.forEach { status ->
                    TextButton(
                        onClick = { onStatusSelected(status) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(status.displayName, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun BulkCollectionDialog(
    collections: List<Collection>,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onCollectionSelected: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $selectedCount games to:") },
        text = {
            if (collections.isEmpty()) {
                Text(
                    "No collections yet. Create one in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column {
                    collections.forEach { collection ->
                        TextButton(
                            onClick = { onCollectionSelected(collection.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(collection.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Sort ──────────────────────────────────────────────────

@Composable
private fun SortDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    currentSort: SortOrder,
    onSortSelected: (SortOrder) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SortOrder.entries.forEach { order ->
            DropdownMenuItem(
                text = { Text(order.displayName) },
                onClick = { onSortSelected(order) },
                leadingIcon = {
                    if (order == currentSort) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }
    }
}

// ── Filter rows ───────────────────────────────────────────

@Composable
private fun CollectionFilterRow(
    collections: List<Collection>,
    selectedCollectionId: Long?,
    onSelected: (Long?) -> Unit,
) {
    if (collections.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedCollectionId == null,
            onClick = { onSelected(null) },
            label = { Text("All", style = MaterialTheme.typography.labelSmall) },
        )
        collections.forEach { collection ->
            FilterChip(
                selected = selectedCollectionId == collection.id,
                onClick = {
                    onSelected(if (selectedCollectionId == collection.id) null else collection.id)
                },
                label = {
                    Text(collection.name, style = MaterialTheme.typography.labelSmall)
                },
            )
        }
    }
}

@Composable
private fun StatusFilterRow(
    selected: GameStatusFilter,
    onSelected: (GameStatusFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GameStatusFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelected(filter) },
                label = {
                    Text(
                        when (filter) {
                            GameStatusFilter.ALL -> "All"
                            GameStatusFilter.NOT_STARTED -> "New"
                            GameStatusFilter.PLAYING -> "Playing"
                            GameStatusFilter.COMPLETED -> "Done"
                            GameStatusFilter.REPLAYING -> "Replay"
                            GameStatusFilter.PAUSED -> "Paused"
                            GameStatusFilter.ABANDONED -> "Dropped"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────

@Composable
private fun EmptyLibrary(
    onAddGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Gamepad,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your library is empty",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add your first game to start tracking progress",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}
