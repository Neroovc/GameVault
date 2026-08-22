package com.gamevault.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamevault.app.R
import com.gamevault.app.domain.model.Collection
import com.gamevault.app.data.settings.GridMode
import com.gamevault.app.data.settings.RatingStyle
import com.gamevault.app.data.settings.StatusStyle
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.ui.components.GameCard
import com.gamevault.app.ui.components.statusColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onGameClick: (Long) -> Unit,
    onCollectionsClick: () -> Unit = {},
    selectionViewModel: LibrarySelectionViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val updateAvailableCount by viewModel.updateAvailableCount.collectAsState()
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
    var showBulkTagDialog by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshMessage by viewModel.refreshMessage.collectAsState()
    LaunchedEffect(refreshMessage) {
        refreshMessage?.let { message ->
            // Clear before showing so a recomposition (e.g. rotation) cannot
            // re-show a stale message.
            viewModel.clearRefreshMessage()
            snackbarHostState.showSnackbar(message)
        }
    }

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { uiState.stripTabs.size })
    val pageData by viewModel.pageData.collectAsState()
    // One scroll position per strip tab, so each category remembers where the
    // user left off instead of sharing a single grid state across pages.
    val scrollStates = remember { mutableStateMapOf<String, LazyGridState>() }

    val hasActiveFilters = uiState.selectedStatus != GameStatusFilter.ALL ||
        uiState.selectedCollectionId != null ||
        uiState.groupBy != GroupBy.NONE

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "GameVault",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Spacer(Modifier.width(8.dp))
                                CountBadge(
                                    count = uiState.stripTabs.firstOrNull()?.count ?: 0,
                                )
                                if (updateAvailableCount > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    UpdateAvailableBadge(count = updateAvailableCount)
                                }
                            }
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
                            IconButton(onClick = {
                                // Same source the active page renders: cached
                                // page data when available, else the live
                                // filtered list (search or settled page).
                                val tabKey = uiState.stripTabs.getOrNull(pagerState.settledPage)?.key ?: ""
                                val visibleIds = (pageData[tabKey]?.flat ?: uiState.displayItems.flat)
                                    .filterIsInstance<DisplayItem.GameItem>()
                                    .map { it.game.id }
                                val randomId = visibleIds.randomOrNull()
                                if (randomId == null) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("No games in library")
                                    }
                                } else {
                                    onGameClick(randomId)
                                }
                            }) {
                                Icon(Icons.Default.Casino, contentDescription = "Random game")
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
        bottomBar = {
            if (isSelectionMode) {
                SelectionBottomBar(
                    onStatusChange = { showBulkStatusDialog = true },
                    onAddToCollection = { showBulkCollectionDialog = true },
                    onAddTag = { showBulkTagDialog = true },
                    onDelete = { showBulkDeleteDialog = true },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            val stripTabs = uiState.stripTabs

            // Category strip — glued to the app bar, hidden while searching.
            if (stripTabs.isNotEmpty() && uiState.searchQuery.isBlank()) {
                CategoryStrip(
                    tabs = stripTabs,
                    pagerState = pagerState,
                    selectedTabIndex = pagerState.settledPage.coerceIn(0, stripTabs.lastIndex),
                    onTabSelected = { index ->
                        // Push the filter at TAP time (not after the animation
                        // settles) so the new page's data is already flowing in
                        // while the pager animates. The settledPage effect below
                        // stays as a sync backup for manual swipes.
                        viewModel.onPagerTabSelected(stripTabs, index)
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // Content
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text("Loading...") }
            } else {
                val targetPage = pageIndexForActiveFilter(
                    tabs = stripTabs,
                    group = uiState.groupBy,
                    selectedCollectionId = uiState.selectedCollectionId,
                    activeGroupKey = uiState.activeGroupKey,
                )

                // While an external change (e.g. a GroupBy switch) is scrolling
                // the pager to a new target, the still-stale settle page belongs
                // to the previous axis. Suppress the pager->VM hand-off until the
                // scroll to the new position actually lands, so a stale index is
                // never pushed into the VM as the active group.
                var pagerAxisResetting by rememberSaveable { mutableStateOf(false) }

                // External filter changes must scroll the pager to the matching
                // page. Only scroll when the pager drifted from its target, so
                // the two-way sync never loops back on itself. A user-initiated
                // tab animation is in flight during tap-driven switches — never
                // yank the pager mid-animation, let it land on its own.
                LaunchedEffect(targetPage, uiState.groupBy, stripTabs.size) {
                    if (stripTabs.isNotEmpty() && !pagerState.isScrollInProgress) {
                        val clamped = targetPage.coerceIn(0, stripTabs.lastIndex)
                        if (pagerState.currentPage != clamped) {
                            pagerAxisResetting = true
                            try {
                                pagerState.scrollToPage(clamped)
                            } finally {
                                pagerAxisResetting = false
                            }
                        }
                    }
                }

                // Pager page changes -> push the page's filter into the VM.
                // (groupBy is part of the keys so an axis switch relaunches the
                // driver, and the flag neutralizes the still-stale settle.)
                LaunchedEffect(pagerState.settledPage, stripTabs.size, uiState.groupBy) {
                    if (stripTabs.isNotEmpty() && !pagerAxisResetting) {
                        viewModel.onPagerTabSelected(stripTabs, pagerState.settledPage)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    // Each page renders its OWN tab's cached data, so the
                    // incoming page never flashes the previous category's items
                    // during the switch animation. Fallbacks: while searching,
                    // pages keep showing the search results (pre-pager behavior);
                    // on cache misses the currently-settled page can fall back
                    // to the live uiState (it IS that category's data).
                    val tabKey = uiState.stripTabs.getOrNull(page)?.key ?: ""
                    val cached = pageData[tabKey]
                    val displayItems = when {
                        cached != null ->
                            if (page == 0 && uiState.groupBy != GroupBy.NONE) cached.withHeaders
                            else cached.flat
                        uiState.searchQuery.isNotBlank() || page == pagerState.settledPage ->
                            if (page == 0 && uiState.groupBy != GroupBy.NONE) uiState.displayItems.withHeaders
                            else uiState.displayItems.flat
                        else -> emptyList<DisplayItem>()
                    }
                    // Fake spinner: the real update job runs in the background
                    // (ViewModel), so each page only shows a cosmetic 1s spin.
                    var isRefreshing by remember(pagerState.currentPage) {
                        mutableStateOf(false)
                    }
                    LibraryGridPage(
                        uiState = uiState,
                        displayItems = displayItems,
                        groupBy = uiState.groupBy,
                        listState = scrollStates.getOrPut(tabKey) { LazyGridState() },
                        selectedIds = selectedIds,
                        isSelectionMode = isSelectionMode,
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            scope.launch {
                                delay(1_000)
                                isRefreshing = false
                            }
                            viewModel.refreshLibrary()
                        },
                        selectionViewModel = selectionViewModel,
                        onGameClick = onGameClick,
                    )
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
            onShowEngineChanged = viewModel::onShowEngineChanged,
            onShowSourceChanged = viewModel::onShowSourceChanged,
            onShowStatusChanged = viewModel::onShowStatusChanged,
            onStatusStyleChanged = viewModel::onStatusStyleChanged,
            ratingStyle = uiState.ratingStyle,
            onRatingStyleChanged = viewModel::onRatingStyleChanged,
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
                val name = collections.firstOrNull { it.id == collectionId }?.name ?: "collection"
                viewModel.addGamesToCollection(selectedIds.toList(), collectionId)
                scope.launch {
                    snackbarHostState.showSnackbar("Added $selectedCount games to $name")
                }
                selectionViewModel.clearSelection()
                showBulkCollectionDialog = false
            },
        )
    }
    if (showBulkTagDialog) {
        BulkTagDialog(
            tags = tags,
            selectedCount = selectedCount,
            onDismiss = { showBulkTagDialog = false },
            onTagSelected = { tagId ->
                val name = tags.firstOrNull { it.id == tagId }?.name ?: "tag"
                viewModel.addTagsToGames(selectedIds.toList(), tagId)
                scope.launch {
                    snackbarHostState.showSnackbar("Tagged $selectedCount games with $name")
                }
                selectionViewModel.clearSelection()
                showBulkTagDialog = false
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
//  CATEGORY STRIP + PAGER
// ═══════════════════════════════════════════════════════════

/**
 * Mihon-style category strip glued under the app bar. The selected tab follows
 * the [pagerState], so the indicator slides along as pages are swiped.
 */
@Composable
private fun CategoryStrip(
    tabs: List<StripTab>,
    pagerState: PagerState,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        edgePadding = 16.dp,
        indicator = { tabPositions ->
            PagerStripIndicator(
                pagerState = pagerState,
                tabPositions = tabPositions,
            )
        },
        divider = {},
        modifier = modifier,
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (tab.count > 0) {
                            Spacer(Modifier.width(4.dp))
                            CountBadge(
                                count = tab.count,
                                style = MaterialTheme.typography.labelSmall,
                                horizontalPadding = 8.dp,
                                verticalPadding = 2.dp,
                            )
                        }
                    }
                },
            )
        }
    }
}

/**
 * Indicator that tracks the [pagerState]'s current page and its drag fraction,
 * so it slides continuously between tabs while swiping instead of jumping.
 *
 * The per-frame slide is driven entirely from a [graphicsLayer] transform
 * (translationX + scaleX), so dragging never recomposes the indicator — only
 * the layer updates.
 */
@Composable
private fun PagerStripIndicator(
    pagerState: PagerState,
    tabPositions: List<TabPosition>,
) {
    if (tabPositions.isEmpty()) return

    val currentPage = pagerState.currentPage.coerceIn(tabPositions.indices)
    val baseWidth = tabPositions[currentPage].width

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.BottomStart)
            .width(baseWidth)
            .height(StripIndicatorHeight)
            .clip(RoundedCornerShape(percent = 50))
            .graphicsLayer {
                val page = pagerState.currentPage.coerceIn(tabPositions.indices)
                val fraction = pagerState.currentPageOffsetFraction

                val fromIndex: Int
                val toIndex: Int
                when {
                    fraction > 0f && page < tabPositions.lastIndex -> {
                        fromIndex = page
                        toIndex = page + 1
                    }
                    fraction < 0f && page > 0 -> {
                        fromIndex = page - 1
                        toIndex = page
                    }
                    else -> {
                        fromIndex = page
                        toIndex = page
                    }
                }

                val progress = if (fraction >= 0f) fraction else 1f + fraction
                val from = tabPositions[fromIndex]
                val to = tabPositions[toIndex]

                val startX = from.left.toPx() + (to.left.toPx() - from.left.toPx()) * progress
                val endX = from.right.toPx() + (to.right.toPx() - from.right.toPx()) * progress
                translationX = startX
                scaleX = (endX - startX) / baseWidth.toPx()
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .background(MaterialTheme.colorScheme.primary),
    )
}

private val StripIndicatorHeight = 3.dp

@Composable
private fun CountBadge(
    count: Int,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 4.dp,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = "$count",
            style = style,
            modifier = Modifier.padding(
                horizontal = horizontalPadding,
                vertical = verticalPadding,
            ),
        )
    }
}

@Composable
private fun UpdateAvailableBadge(count: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = pluralStringResource(R.plurals.updates_available_badge, count, count),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// ═══════════════════════════════════════════════════════════
//  LIBRARY GRID PAGE
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryGridPage(
    uiState: LibraryUiState,
    displayItems: List<DisplayItem>,
    groupBy: GroupBy,
    listState: LazyGridState,
    selectedIds: Set<Long>,
    isSelectionMode: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    selectionViewModel: LibrarySelectionViewModel,
    onGameClick: (Long) -> Unit,
) {
    if (displayItems.isEmpty()) {
        EmptyLibrary(modifier = Modifier.fillMaxSize())
        return
    }

    // Mihon-style pull-to-refresh, disabled while multi-selecting so the
    // gesture never fights the selection interactions.
    val pullRefreshState = rememberPullToRefreshState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                enabled = !isSelectionMode,
                onRefresh = onRefresh,
            ),
    ) {
        LazyVerticalGrid(
            columns = if (uiState.gridMode == GridMode.LIST) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            displayItems.forEach { item ->
                when (item) {
                    is DisplayItem.Header -> {
                        item(
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = "header",
                        ) {
                            GroupHeader(
                                label = item.label,
                                count = item.count,
                                accentColor = groupHeaderAccent(groupBy, item.groupKey),
                            )
                        }
                    }
                    is DisplayItem.GameItem -> {
                        item(key = item.uniqueKey, contentType = "game") {
                            GameCard(
                                game = item.game,
                                isSelected = item.game.id in selectedIds,
                                gridMode = uiState.gridMode,
                                showEngine = uiState.showEngine,
                                showSource = uiState.showSource,
                                showStatus = uiState.showStatus,
                                statusStyle = uiState.statusStyle,
                                ratingStyle = uiState.ratingStyle,
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

        PullToRefreshDefaults.Indicator(
            state = pullRefreshState,
            isRefreshing = isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * Mihon-style section header: a small vertical color strip (status accent for
 * status groups, primary for source/category), label and count, over a subtly
 * tinted row.
 */
@Composable
private fun GroupHeader(
    label: String,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(GroupHeaderStripWidth)
                .fillMaxHeight()
                .background(accentColor),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        CountBadge(count = count)
    }
}

private val GroupHeaderStripWidth = 4.dp

@Composable
private fun groupHeaderAccent(group: GroupBy, groupKey: String): Color {
    if (group == GroupBy.STATUS && groupKey.isNotEmpty()) {
        return statusColor(GameStatus.valueOf(groupKey))
    }
    return MaterialTheme.colorScheme.primary
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
    onShowEngineChanged: (Boolean) -> Unit,
    onShowSourceChanged: (Boolean) -> Unit,
    onShowStatusChanged: (Boolean) -> Unit,
    onStatusStyleChanged: (StatusStyle) -> Unit,
    ratingStyle: RatingStyle,
    onRatingStyleChanged: (RatingStyle) -> Unit,
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
                        showEngine = uiState.showEngine,
                        onShowEngineChanged = onShowEngineChanged,
                        showSource = uiState.showSource,
                        onShowSourceChanged = onShowSourceChanged,
                        showStatus = uiState.showStatus,
                        onShowStatusChanged = onShowStatusChanged,
                        statusStyle = uiState.statusStyle,
                        onStatusStyleChanged = onStatusStyleChanged,
                        ratingStyle = ratingStyle,
                        onRatingStyleChanged = onRatingStyleChanged,
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
    showEngine: Boolean,
    onShowEngineChanged: (Boolean) -> Unit,
    showSource: Boolean,
    onShowSourceChanged: (Boolean) -> Unit,
    showStatus: Boolean,
    onShowStatusChanged: (Boolean) -> Unit,
    statusStyle: StatusStyle,
    onStatusStyleChanged: (StatusStyle) -> Unit,
    ratingStyle: RatingStyle,
    onRatingStyleChanged: (RatingStyle) -> Unit,
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
            label = "Engine",
            checked = showEngine,
            onCheckedChange = onShowEngineChanged,
        )
        OverlayToggleRow(
            label = "Source",
            checked = showSource,
            onCheckedChange = onShowSourceChanged,
        )
        OverlayToggleRow(
            label = "Status",
            checked = showStatus,
            onCheckedChange = onShowStatusChanged,
        )
        if (showStatus) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusStyle.entries.forEach { style ->
                    FilterChip(
                        selected = statusStyle == style,
                        onClick = { onStatusStyleChanged(style) },
                        label = { Text(style.displayName, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        SectionLabel("Rating style")
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RatingStyle.entries.forEach { style ->
                FilterChip(
                    selected = ratingStyle == style,
                    onClick = { onRatingStyleChanged(style) },
                    label = { Text(style.displayName, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
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
    onAddTag: () -> Unit,
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
            Button(onClick = onAddTag) { Text("Tag") }
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

@Composable
private fun BulkTagDialog(
    tags: List<Tag>,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onTagSelected: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tag $selectedCount games with:") },
        text = {
            if (tags.isEmpty()) {
                Text("No tags yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column {
                    tags.forEach { tag ->
                        TextButton(
                            onClick = { onTagSelected(tag.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(tag.name, modifier = Modifier.fillMaxWidth()) }
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
private fun EmptyLibrary(modifier: Modifier = Modifier) {
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
        Text("Your library is empty. Browse sources to add your first game",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center)
    }
}