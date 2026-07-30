package com.gamevault.app.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
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
    var showSheet by remember { mutableStateOf(false) }
    var showBulkStatusDialog by remember { mutableStateOf(false) }
    var showBulkCollectionDialog by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val displayItems = remember(uiState.games, uiState.groupBy) {
        viewModel.computeDisplayItems(uiState.games, uiState.groupBy)
    }

    val hasActiveFilters = uiState.selectedStatus != GameStatusFilter.ALL ||
        uiState.selectedCollectionId != null ||
        uiState.groupBy != GroupBy.NONE

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
                        IconButton(onClick = { showSheet = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Filter & Sort",
                                tint = if (hasActiveFilters) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
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
            }

            // Content
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text("Loading...") }
            } else if (displayItems.isEmpty()) {
                EmptyLibrary(onAddGame = onAddGame, modifier = Modifier.fillMaxSize())
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
                        displayItems.forEach { item ->
                            when (item) {
                                is DisplayItem.Header -> {
                                    item(
                                        span = { GridItemSpan(maxLineSpan) },
                                        contentType = "header",
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                        ) {
                                            Text(
                                                text = "${item.label} (${item.count})",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                                is DisplayItem.GameItem -> {
                                    item(key = item.uniqueKey, contentType = "game") {
                                        GameCard(
                                            game = item.game,
                                            isSelected = item.game.id in selectedIds,
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectionViewModel.toggleSelection(item.game.id)
                                                } else {
                                                    onGameClick(item.game.id)
                                                }
                                            },
                                            onLongClick = {
                                                selectionViewModel.toggleSelection(item.game.id)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Filter/Sort/Group Bottom Sheet ─────────────────────
    if (showSheet) {
        FilterSortSheet(
            uiState = uiState,
            collections = collections,
            onDismiss = { showSheet = false },
            onStatusChanged = viewModel::onStatusFilterChanged,
            onCollectionChanged = viewModel::onCollectionFilterChanged,
            onSortChanged = viewModel::onSortOrderChanged,
            onGroupChanged = viewModel::onGroupByChanged,
        )
    }

    // ── Dialogs ─────────────────────────────────────────────
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
                TextButton(onClick = {
                    viewModel.deleteGames(selectedIds.toList())
                    selectionViewModel.clearSelection()
                    showBulkDeleteDialog = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════
//  FILTER / SORT / GROUP BOTTOM SHEET
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSortSheet(
    uiState: LibraryUiState,
    collections: List<Collection>,
    onDismiss: () -> Unit,
    onStatusChanged: (GameStatusFilter) -> Unit,
    onCollectionChanged: (Long?) -> Unit,
    onSortChanged: (SortOrder) -> Unit,
    onGroupChanged: (GroupBy) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Filter & Sort",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            // ── Section: Filter ──────────────────────────────
            SectionLabel("Filter")
            StatusChipsRow(
                selected = uiState.selectedStatus,
                onSelected = onStatusChanged,
            )
            if (collections.isNotEmpty()) {
                CollectionChipsRow(
                    collections = collections,
                    selectedId = uiState.selectedCollectionId,
                    onSelected = onCollectionChanged,
                )
            }

            HorizontalDivider()

            // ── Section: Sort ────────────────────────────────
            SectionLabel("Sort")
            SortOptions(
                current = uiState.sortOrder,
                onSelected = onSortChanged,
            )

            HorizontalDivider()

            // ── Section: Group ───────────────────────────────
            SectionLabel("Group")
            GroupOptions(
                current = uiState.groupBy,
                onSelected = onGroupChanged,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

// ── Filter: Status chips ──────────────────────────────────

@Composable
private fun StatusChipsRow(
    selected: GameStatusFilter,
    onSelected: (GameStatusFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
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

// ── Filter: Collection chips ──────────────────────────────

@Composable
private fun CollectionChipsRow(
    collections: List<Collection>,
    selectedId: Long?,
    onSelected: (Long?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedId == null,
            onClick = { onSelected(null) },
            label = { Text("All", style = MaterialTheme.typography.labelSmall) },
        )
        collections.forEach { coll ->
            FilterChip(
                selected = selectedId == coll.id,
                onClick = { onSelected(if (selectedId == coll.id) null else coll.id) },
                label = { Text(coll.name, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

// ── Sort options ──────────────────────────────────────────

@Composable
private fun SortOptions(
    current: SortOrder,
    onSelected: (SortOrder) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            SortOrder.entries.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    row.forEach { order ->
                        NavigationBarItem(
                            selected = order == current,
                            onClick = { onSelected(order) },
                            icon = {
                                if (order == current) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            },
                            label = {
                                Text(
                                    order.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                )
                            },
                            alwaysShowLabel = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

// ── Group options ─────────────────────────────────────────

@Composable
private fun GroupOptions(
    current: GroupBy,
    onSelected: (GroupBy) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GroupBy.entries.forEach { group ->
            FilterChip(
                selected = group == current,
                onClick = { onSelected(group) },
                label = { Text(group.displayName, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  SELECTION UI
// ═══════════════════════════════════════════════════════════

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
    BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete") }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  DIALOGS
// ═══════════════════════════════════════════════════════════

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
                    ) { Text(status.displayName, modifier = Modifier.fillMaxWidth()) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
                Text("No collections yet. Create one in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column {
                    collections.forEach { collection ->
                        TextButton(
                            onClick = { onCollectionSelected(collection.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(collection.name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ═══════════════════════════════════════════════════════════
//  EMPTY STATE
// ═══════════════════════════════════════════════════════════

@Composable
private fun EmptyLibrary(onAddGame: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Gamepad, contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(16.dp))
        Text("Your library is empty", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("Add your first game to start tracking progress",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center)
    }
}
