package com.gamevault.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamevault.app.domain.model.Collection
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.domain.model.SourceType
import com.gamevault.app.domain.model.Tag
import com.gamevault.app.data.settings.AppSettings
import com.gamevault.app.data.settings.GridMode
import com.gamevault.app.data.settings.RatingStyle
import com.gamevault.app.data.settings.StatusStyle
import com.gamevault.app.domain.repository.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MS = 250L

data class LibraryUiState(
    val games: List<Game> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val selectedStatus: GameStatusFilter = GameStatusFilter.ALL,
    val sortOrder: SortOrder = SortOrder.DATE_ADDED_DESC,
    val selectedCollectionId: Long? = null,
    val groupBy: GroupBy = GroupBy.NONE,
    val activeGroupKey: String? = null,
    val stripTabs: List<StripTab> = emptyList(),
    val gridMode: GridMode = GridMode.COMFORTABLE,
    val showEngine: Boolean = true,
    val showSource: Boolean = true,
    val showStatus: Boolean = true,
    val statusStyle: StatusStyle = StatusStyle.TOP_BAR,
    val ratingStyle: RatingStyle = RatingStyle.STAR,
    val displayItems: LibraryDisplayItems = LibraryDisplayItems(emptyList(), emptyList()),
)

enum class GameStatusFilter {
    ALL, NOT_STARTED, PLAYING, COMPLETED, REPLAYING, PAUSED, ABANDONED
}

enum class SortOrder(val displayName: String) {
    DATE_ADDED_DESC("Recently Added"),
    DATE_ADDED_ASC("Oldest First"),
    LAST_PLAYED("Last Played"),
    RECENTLY_UPDATED("Recently Updated"),
    TITLE_ASC("Title A-Z"),
    TITLE_DESC("Title Z-A"),
    RATING("Rating"),
    PLAYTIME("Play Time"),
}

enum class GroupBy(val displayName: String) {
    NONE("None"),
    STATUS("Status"),
    SOURCE("Source"),
    CATEGORY("Category"),
}

/**
 * One tab of the app-bar category strip. Counts are derived from the RAW library,
 * not the currently filtered grid, so each axis always reflects the whole shelf.
 * Each tab carries the [key] of the filter its page controls: null for "All",
 * otherwise the collection id / status / source name.
 */
sealed class StripTab {
    abstract val label: String
    abstract val key: String?
    abstract val count: Int

    data class AllTab(override val count: Int) : StripTab() {
        override val label: String get() = "All"
        override val key: String? = null
    }

    data class CollectionTab(val collection: Collection, override val count: Int) : StripTab() {
        override val label: String get() = collection.name
        override val key: String get() = collection.id.toString()
    }

    data class StatusTab(val status: GameStatus, override val count: Int) : StripTab() {
        override val label: String get() = status.displayName
        override val key: String get() = status.name
    }

    data class SourceTab(val source: SourceType, override val count: Int) : StripTab() {
        override val label: String get() = source.displayName
        override val key: String get() = source.name
    }
}

sealed class DisplayItem {
    abstract val uniqueKey: String

    data class Header(
        val label: String,
        val count: Int,
        val groupKey: String = "",
    ) : DisplayItem() {
        override val uniqueKey get() = "hdr:$label"
    }

    data class GameItem(
        val game: Game,
        val groupKey: String = "",
    ) : DisplayItem() {
        override val uniqueKey get() = if (groupKey.isEmpty()) "g:${game.id}" else "g:$groupKey:${game.id}"
    }
}

data class LibraryDisplayItems(
    val withHeaders: List<DisplayItem>,
    val flat: List<DisplayItem>,
)

class LibraryViewModel(
    private val repository: GameRepository,
    private val appSettings: AppSettings,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedStatus = MutableStateFlow(GameStatusFilter.ALL)
    val selectedStatus: StateFlow<GameStatusFilter> = _selectedStatus.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _selectedCollectionId = MutableStateFlow<Long?>(null)
    val selectedCollectionId: StateFlow<Long?> = _selectedCollectionId.asStateFlow()

    private val _activeGroupKey = MutableStateFlow<String?>(null)
    val activeGroupKey: StateFlow<String?> = _activeGroupKey.asStateFlow()

    private val _groupBy = MutableStateFlow(GroupBy.NONE)
    private val _gridMode = MutableStateFlow(GridMode.COMFORTABLE)
    val gridMode: StateFlow<GridMode> = _gridMode.asStateFlow()

    private val _refreshMessage = MutableStateFlow<String?>(null)
    val refreshMessage: StateFlow<String?> = _refreshMessage.asStateFlow()

    /** In-flight refresh pass — a new pull must not start a concurrent one. */
    private var refreshJob: Job? = null

    val collections: StateFlow<List<Collection>> = repository.observeAllCollections()
    val tags: StateFlow<List<Tag>> = repository.observeAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Games flagged by the update worker as having a newer version upstream. */
    val updateAvailableCount: StateFlow<Int> = repository.observeUpdateAvailableCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // Raw library — strip tab counts must reflect the whole shelf, not the
    // currently filtered grid.
    private val allGames: StateFlow<List<Game>> = repository.observeAllGames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            appSettings.gridMode.collect { mode ->
                _gridMode.value = mode
            }
        }
    }

    private data class FilterParams(
        val query: String,
        val status: GameStatusFilter,
        val sort: SortOrder,
        val collectionId: Long?,
    )

    private data class AxisParams(
        val status: GameStatusFilter,
        val collectionId: Long?,
        val groupKey: String?,
    )

    private val axisParams: Flow<AxisParams> = combine(
        _selectedStatus,
        _selectedCollectionId,
        _activeGroupKey,
    ) { status, collectionId, groupKey ->
        AxisParams(status, collectionId, groupKey)
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val queryState: StateFlow<LibraryUiState> = combine(
        _searchQuery.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS },
        axisParams,
        _sortOrder,
        _groupBy,
    ) { query, axis, sort, group ->
        FilterParams(query, axis.status, sort, axis.collectionId) to group
    }.flatMapLatest { (params, group) ->
        // The strip page is the authority for its own axis; the sheet's chips
        // only apply on the collection axis (NONE/CATEGORY) and as the plain
        // status filter when not grouped by status.
        val effectiveCollectionId = if (group == GroupBy.NONE || group == GroupBy.CATEGORY) {
            params.collectionId
        } else null

        val effectiveStatus: GameStatus? = when (group) {
            GroupBy.STATUS -> _activeGroupKey.value?.let(GameStatus::valueOf)
            GroupBy.NONE, GroupBy.CATEGORY ->
                if (params.status == GameStatusFilter.ALL) null else GameStatus.valueOf(params.status.name)
            GroupBy.SOURCE -> null
        }

        val effectiveSource: SourceType? = when (group) {
            GroupBy.SOURCE -> _activeGroupKey.value?.let(SourceType::valueOf)
            else -> null
        }

        val source: Flow<List<Game>> = when {
            params.query.isNotBlank() -> repository.searchGames(escapeLikeQuery(params.query)).map { games ->
                games.filter { game ->
                    (effectiveStatus == null || game.status == effectiveStatus) &&
                        (effectiveSource == null || game.sourceType == effectiveSource) &&
                        (effectiveCollectionId == null || game.collections.any { it.id == effectiveCollectionId })
                }
            }
            effectiveCollectionId != null -> repository.observeGamesInCollection(effectiveCollectionId).map { games ->
                if (effectiveStatus == null) games else games.filter { it.status == effectiveStatus }
            }
            effectiveStatus != null -> repository.observeGamesByStatus(effectiveStatus)
            effectiveSource != null -> repository.observeAllGames().map { games ->
                games.filter { it.sourceType == effectiveSource }
            }
            else -> repository.observeAllGames()
        }

        source.map { games -> sortGames(games, params.sort) }
            .catch { emit(emptyList<Game>()) }
            .map { sortedGames ->
                LibraryUiState(
                    games = sortedGames,
                    // searchQuery is overridden by the outer combine with the raw (undebounced) value
                    searchQuery = "",
                    isLoading = false,
                    selectedStatus = _selectedStatus.value,
                    sortOrder = _sortOrder.value,
                    selectedCollectionId = _selectedCollectionId.value,
                    groupBy = _groupBy.value,
                    activeGroupKey = _activeGroupKey.value,
                )
            }
            .flowOn(Dispatchers.Default)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(isLoading = true),
    )

    private data class OverlayPrefs(
        val showEngine: Boolean,
        val showSource: Boolean,
        val showStatus: Boolean,
        val statusStyle: StatusStyle,
        val ratingStyle: RatingStyle,
    )

    private val overlayPrefs: Flow<OverlayPrefs> = combine(
        appSettings.showEngine,
        appSettings.showSource,
        appSettings.showStatus,
        appSettings.statusStyle,
        appSettings.ratingStyle,
    ) { engine, source, status, style, ratingStyle ->
        OverlayPrefs(engine, source, status, style, ratingStyle)
    }

    private val stripTabsFlow: StateFlow<List<StripTab>> = combine(
        allGames,
        collections,
        _groupBy,
    ) { games, colls, group ->
        computeStripTabs(group, games, colls)
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /**
     * Per-tab display items, computed ahead of time for EVERY strip tab from
     * the in-memory library. Each pager page reads its own entry (keyed by the
     * tab's filter key), so the incoming page shows its own category instantly
     * while the pager animates — no more stale "previous category" flash.
     */
    val pageData: StateFlow<Map<String, LibraryDisplayItems>> = combine(
        allGames,
        collections,
        _sortOrder,
        _groupBy,
    ) { all, colls, sort, group ->
        val tabs = computeStripTabs(group, all, colls)
        val byKey = HashMap<String, LibraryDisplayItems>()
        for (tab in tabs) {
            val tabGames = when (tab) {
                is StripTab.AllTab -> all
                is StripTab.CollectionTab -> all.filter { game ->
                    game.collections.any { it.id == tab.collection.id }
                }
                is StripTab.StatusTab -> all.filter { it.status == tab.status }
                is StripTab.SourceTab -> all.filter { it.sourceType == tab.source }
            }
            val sorted = sortGames(tabGames, sort)
            byKey[tab.key ?: ""] = LibraryDisplayItems(
                withHeaders = if (group != GroupBy.NONE) {
                    computeDisplayItems(sorted, group, showHeaders = true)
                } else {
                    emptyList()
                },
                flat = computeDisplayItems(sorted, group, showHeaders = false),
            )
        }
        byKey
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap(),
    )

    val uiState: StateFlow<LibraryUiState> = combine(
        queryState,
        _gridMode,
        overlayPrefs,
        _searchQuery,
        stripTabsFlow,
    ) { state, mode, prefs, rawQuery, tabs ->
        val displayItems = LibraryDisplayItems(
            flat = computeDisplayItems(state.games, state.groupBy, showHeaders = false),
            withHeaders = if (state.groupBy != GroupBy.NONE) {
                computeDisplayItems(state.games, state.groupBy, showHeaders = true)
            } else {
                emptyList()
            },
        )
        state.copy(
            gridMode = mode,
            showEngine = prefs.showEngine,
            showSource = prefs.showSource,
            showStatus = prefs.showStatus,
            statusStyle = prefs.statusStyle,
            ratingStyle = prefs.ratingStyle,
            searchQuery = rawQuery,
            stripTabs = tabs,
            displayItems = displayItems,
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(isLoading = true),
    )

    // ── Public API ──────────────────────────────────────────

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }
    fun onStatusFilterChanged(status: GameStatusFilter) { _selectedStatus.value = status }
    fun onSortOrderChanged(order: SortOrder) { _sortOrder.value = order }

    fun onCollectionFilterChanged(collectionId: Long?) {
        _selectedCollectionId.value = collectionId
    }

    fun onGroupByChanged(group: GroupBy) {
        _groupBy.value = group
        // A fresh axis always starts on its "All" tab.
        _activeGroupKey.value = null
    }

    fun onGridModeChanged(mode: GridMode) {
        _gridMode.value = mode
        viewModelScope.launch { appSettings.setGridMode(mode) }
    }

    fun onShowEngineChanged(value: Boolean) {
        viewModelScope.launch { appSettings.setShowEngine(value) }
    }

    fun onShowSourceChanged(value: Boolean) {
        viewModelScope.launch { appSettings.setShowSource(value) }
    }

    fun onShowStatusChanged(value: Boolean) {
        viewModelScope.launch { appSettings.setShowStatus(value) }
    }

    fun onStatusStyleChanged(style: StatusStyle) {
        viewModelScope.launch { appSettings.setStatusStyle(style) }
    }

    fun onRatingStyleChanged(style: RatingStyle) {
        viewModelScope.launch { appSettings.setRatingStyle(style) }
    }

    fun deleteGame(gameId: Long) {
        viewModelScope.launch { repository.deleteGame(gameId) }
    }

    fun updateGameStatusBulk(gameIds: List<Long>, status: GameStatus) {
        viewModelScope.launch { repository.updateGameStatusBulk(gameIds, status) }
    }

    fun addGamesToCollection(gameIds: List<Long>, collectionId: Long) {
        viewModelScope.launch { repository.addGamesToCollection(gameIds, collectionId) }
    }

    fun addTagsToGames(gameIds: List<Long>, tagId: Long) {
        viewModelScope.launch { repository.addTagsToGames(gameIds, tagId) }
    }

    fun deleteGames(gameIds: List<Long>) {
        viewModelScope.launch { repository.deleteGames(gameIds) }
    }

    /**
     * Fire-and-forget library update: re-scrape every saved game's metadata
     * in the background and surface the outcome as a snackbar message.
     */
    fun refreshLibrary() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val result = repository.refreshSavedGames()
            _refreshMessage.value = when {
                result.errors > 0 -> "Update errors: ${result.errors}"
                result.updated > 0 -> "${result.updated} games updated"
                else -> "No updates found"
            }
        }
    }

    fun clearRefreshMessage() {
        _refreshMessage.value = null
    }

    /**
     * The pager settled on [page] — push the page's filter to the axis state.
     * Guards against redundant updates (no-op when the filter already matches),
     * which breaks the pager <-> VM feedback loop.
     */
    fun onPagerTabSelected(tabs: List<StripTab>, page: Int) {
        when (_groupBy.value) {
            GroupBy.NONE, GroupBy.CATEGORY -> {
                val id = (tabs.getOrNull(page) as? StripTab.CollectionTab)?.collection?.id
                if (_selectedCollectionId.value != id) _selectedCollectionId.value = id
            }
            GroupBy.STATUS -> {
                val key = (tabs.getOrNull(page) as? StripTab.StatusTab)?.status?.name
                if (_activeGroupKey.value != key) _activeGroupKey.value = key
            }
            GroupBy.SOURCE -> {
                val key = (tabs.getOrNull(page) as? StripTab.SourceTab)?.source?.name
                if (_activeGroupKey.value != key) _activeGroupKey.value = key
            }
        }
    }

    // ── Grouping ────────────────────────────────────────────

    /**
     * Compute display items from the current games and groupBy setting.
     * [showHeaders] controls the Mihon-style grouped headers: enabled on the
     * "All" page, disabled for a specific group page (where the tab itself is
     * the header).
     */
    fun computeDisplayItems(
        games: List<Game>,
        group: GroupBy,
        showHeaders: Boolean = true,
    ): List<DisplayItem> {
        if (group == GroupBy.NONE || !showHeaders || games.isEmpty()) {
            return games.map { DisplayItem.GameItem(it) }
        }

        return when (group) {
            GroupBy.STATUS -> {
                games.groupBy { it.status }.entries.map { (status, grouped) ->
                    listOf(DisplayItem.Header(status.displayName, grouped.size, status.name)) +
                        grouped.map { DisplayItem.GameItem(it, status.name) }
                }.flatten()
            }
            GroupBy.SOURCE -> {
                games.groupBy { it.sourceType }.entries.map { (source, grouped) ->
                    listOf(DisplayItem.Header(source.displayName, grouped.size, source.name)) +
                        grouped.map { DisplayItem.GameItem(it, source.name) }
                }.flatten()
            }
            GroupBy.CATEGORY -> {
                // A game can be in multiple collections — dupe across groups
                val grouped = mutableMapOf<String, MutableList<Game>>()
                for (game in games) {
                    if (game.collections.isEmpty()) {
                        grouped.getOrPut("Uncategorized") { mutableListOf() }.add(game)
                    } else {
                        for (coll in game.collections) {
                            grouped.getOrPut(coll.name) { mutableListOf() }.add(game)
                        }
                    }
                }
                grouped.entries.map { (label, groupedGames) ->
                    listOf(DisplayItem.Header(label, groupedGames.size)) +
                        groupedGames.map { DisplayItem.GameItem(it, label) }
                }.flatten()
            }
            GroupBy.NONE -> games.map { DisplayItem.GameItem(it) }
        }
    }

    // ── Strip tabs ──────────────────────────────────────────

    private fun computeStripTabs(
        group: GroupBy,
        all: List<Game>,
        collections: List<Collection>,
    ): List<StripTab> = when (group) {
        GroupBy.NONE, GroupBy.CATEGORY -> {
            val counts = HashMap<Long, Int>()
            for (game in all) {
                for (coll in game.collections) {
                    counts[coll.id] = (counts[coll.id] ?: 0) + 1
                }
            }
            listOf(StripTab.AllTab(all.size)) + collections.map { coll ->
                StripTab.CollectionTab(coll, counts[coll.id] ?: 0)
            }
        }
        GroupBy.STATUS -> {
            val counts = HashMap<GameStatus, Int>()
            for (game in all) {
                counts[game.status] = (counts[game.status] ?: 0) + 1
            }
            listOf(StripTab.AllTab(all.size)) + GameStatus.entries
                .map { status -> StripTab.StatusTab(status, counts[status] ?: 0) }
                .filter { it.count > 0 }
        }
        GroupBy.SOURCE -> {
            val counts = HashMap<SourceType, Int>()
            for (game in all) {
                counts[game.sourceType] = (counts[game.sourceType] ?: 0) + 1
            }
            listOf(StripTab.AllTab(all.size)) + SourceType.entries
                .map { source -> StripTab.SourceTab(source, counts[source] ?: 0) }
                .filter { it.count > 0 }
        }
    }

    // ── Search ─────────────────────────────────────────────

    /**
     * Escape SQL LIKE wildcards so user input is matched literally.
     * Must mirror the ESCAPE '\' clause in GameDao.searchGamesFlow.
     */
    private fun escapeLikeQuery(raw: String): String =
        raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    // ── Sorting ─────────────────────────────────────────────

    private fun sortGames(games: List<Game>, order: SortOrder): List<Game> {
        return when (order) {
            SortOrder.DATE_ADDED_DESC -> games.sortedByDescending { it.dateAdded }
            SortOrder.DATE_ADDED_ASC -> games.sortedBy { it.dateAdded }
            SortOrder.LAST_PLAYED -> games.sortedByDescending { it.lastPlayed ?: 0L }
            SortOrder.RECENTLY_UPDATED ->
                games.sortedWith(
                    compareByDescending<Game> { it.updateAvailable }
                        .thenByDescending { it.lastChecked ?: 0L }
                )
            SortOrder.TITLE_ASC -> games.sortedBy { it.title.lowercase() }
            SortOrder.TITLE_DESC -> games.sortedByDescending { it.title.lowercase() }
            SortOrder.RATING -> games.sortedByDescending { it.personalRating ?: 0f }
            SortOrder.PLAYTIME -> games.sortedByDescending { it.playTimeMinutes }
        }
    }

    class Factory(
        private val repository: GameRepository,
        private val appSettings: AppSettings,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryViewModel(repository, appSettings) as T
    }
}

/**
 * Index of the strip page implied by the current axis filters. 0 = "All".
 * Clamped to the strip's valid range so indices stay valid after the axis
 * recomputes (e.g. a group change that shrinks the tab list).
 */
internal fun pageIndexForActiveFilter(
    tabs: List<StripTab>,
    group: GroupBy,
    selectedCollectionId: Long?,
    activeGroupKey: String?,
): Int {
    val key = when (group) {
        GroupBy.NONE, GroupBy.CATEGORY -> selectedCollectionId?.toString()
        GroupBy.STATUS, GroupBy.SOURCE -> activeGroupKey
    }
    return if (key == null) 0 else tabs.indexOfFirst { it.key == key }.coerceAtLeast(0)
}