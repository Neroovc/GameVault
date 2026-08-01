package com.gamevault.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamevault.app.domain.model.Collection
import com.gamevault.app.data.settings.GridMode
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.ui.components.GameCard
import kotlinx.coroutines.launch

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
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) focusRequester.requestFocus()
    }
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
                    title = {
                        if (searchExpanded) {
                            TextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::onSearchQueryChanged,
                                singleLine = true,
                                placeholder = { Text("Search library") },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.onSearchQueryChanged("") },
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Clear search",
                                            )
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { focusManager.clearFocus() },
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                            )
                        } else {
                            Text("GameVault")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        if (searchExpanded) {
                            IconButton(onClick = {
                                searchExpanded = false
                                viewModel.onSearchQueryChanged("")
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        }
                    },
                    actions = {
                        if (!searchExpanded) {
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
            // Collection chips row (hidden while searching — search takes over the list)
            if (collections.isNotEmpty() && uiState.searchQuery.isBlank()) {
                CollectionChipsRow(
                    collections = collections,
                    selectedId = uiState.selectedCollectionId,
                    onSelected = viewModel::onCollectionFilterChanged,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
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
                        columns = if (uiState.gridMode == GridMode.LIST) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 150.dp),
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
                                            gridMode = uiState.gridMode,
                                            showEngineSource = uiState.showEngineSource,
                                            showStatus = uiState.showStatus,
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
            onGridModeChanged = viewModel::onGridModeChanged,
            onShowEngineSourceChanged = viewModel::onShowEngineSourceChanged,
            onShowStatusChanged = viewModel::onShowStatusChanged,
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
    onGridModeChanged: (GridMode) -> Unit,
    onShowEngineSourceChanged: (Boolean) -> Unit,
    onShowStatusChanged: (Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = SheetMaxHeight),
        ) {
            Text(
                "Library settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            val pagerState = rememberPagerState(pageCount = { SheetTabLabels.size })
            TabRow(selectedTabIndex = pagerState.currentPage) {
                SheetTabLabels.forEachIndexed { index, label ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(label) },
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                when (page) {
                    0 -> FilterTab(
                        selectedStatus = uiState.selectedStatus,
                        onStatusChanged = onStatusChanged,
                        collections = collections,
                        selectedCollectionId = uiState.selectedCollectionId,
                        onCollectionChanged = onCollectionChanged,
                    )
                    1 -> SortTab(
                        current = uiState.sortOrder,
                        onSelected = onSortChanged,
                    )
                    2 -> AppearanceTab(
                        gridMode = uiState.gridMode,
                        onGridModeChanged = onGridModeChanged,
                        showEngineSource = uiState.showEngineSource,
                        onShowEngineSourceChanged = onShowEngineSourceChanged,
                        showStatus = uiState.showStatus,
                        onShowStatusChanged = onShowStatusChanged,
                    )
                    3 -> GroupTab(
                        current = uiState.groupBy,
                        onSelected = onGroupChanged,
                    )
                }
            }
        }
    }
}

private val SheetMaxHeight = 520.dp
private val SheetTabLabels = listOf("Filter", "Sort", "Appearance", "Group")

@Composable
private fun SheetPageColumn(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun FilterTab(
    selectedStatus: GameStatusFilter,
    onStatusChanged: (GameStatusFilter) -> Unit,
    collections: List<Collection>,
    selectedCollectionId: Long?,
    onCollectionChanged: (Long?) -> Unit,
) {
    SheetPageColumn {
        StatusChipsRow(selected = selectedStatus, onSelected = onStatusChanged)
        if (collections.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            CollectionChipsRow(
                collections = collections,
                selectedId = selectedCollectionId,
                onSelected = onCollectionChanged,
            )
        }
    }
}

@Composable
private fun SortTab(
    current: SortOrder,
    onSelected: (SortOrder) -> Unit,
) {
    SheetPageColumn {
        SortOrder.entries.forEach { order ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelected(order) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(order.displayName, style = MaterialTheme.typography.bodyLarge)
                if (order == current) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceTab(
    gridMode: GridMode,
    onGridModeChanged: (GridMode) -> Unit,
    showEngineSource: Boolean,
    onShowEngineSourceChanged: (Boolean) -> Unit,
    showStatus: Boolean,
    onShowStatusChanged: (Boolean) -> Unit,
) {
    SheetPageColumn {
        SectionLabel("Grid mode")
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GridMode.entries.forEach { mode ->
                FilterChip(
                    selected = gridMode == mode,
                    onClick = { onGridModeChanged(mode) },
                    label = { Text(mode.displayName, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        SectionLabel("Overlay")
        OverlayToggleRow(
            label = "Engine & source",
            checked = showEngineSource,
            onCheckedChange = onShowEngineSourceChanged,
        )
        OverlayToggleRow(
            label = "Status",
            checked = showStatus,
            onCheckedChange = onShowStatusChanged,
        )
    }
}

@Composable
private fun GroupTab(
    current: GroupBy,
    onSelected: (GroupBy) -> Unit,
) {
    SheetPageColumn {
        GroupOptions(current = current, onSelected = onSelected)
    }
}

@Composable
private fun OverlayToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    var localChecked by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) { localChecked = checked }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = localChecked,
            onCheckedChange = { newValue ->
                localChecked = newValue
                onCheckedChange(newValue)
            },
        )
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
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
