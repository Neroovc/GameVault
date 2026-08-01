package com.gamevault.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamevault.app.domain.model.Collection
import com.gamevault.app.domain.model.Game
import com.gamevault.app.data.settings.AppSettings
import com.gamevault.app.data.settings.GridMode
import com.gamevault.app.domain.repository.GameRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
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
    val gridMode: GridMode = GridMode.COMFORTABLE,
    val showEngineSource: Boolean = true,
    val showStatus: Boolean = true,
)

enum class GameStatusFilter {
    ALL, NOT_STARTED, PLAYING, COMPLETED, REPLAYING, PAUSED, ABANDONED
}

enum class SortOrder(val displayName: String) {
    DATE_ADDED_DESC("Recently Added"),
    DATE_ADDED_ASC("Oldest First"),
    LAST_PLAYED("Last Played"),
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

sealed class DisplayItem {
    abstract val uniqueKey: String

    data class Header(val label: String, val count: Int) : DisplayItem() {
        override val uniqueKey get() = "hdr:$label"
    }

    data class GameItem(
        val game: Game,
        val groupKey: String = "",
    ) : DisplayItem() {
        override val uniqueKey get() = if (groupKey.isEmpty()) "g:${game.id}" else "g:$groupKey:${game.id}"
    }
}

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

    private val _groupBy = MutableStateFlow(GroupBy.NONE)
    private val _gridMode = MutableStateFlow(GridMode.COMFORTABLE)
    val gridMode: StateFlow<GridMode> = _gridMode.asStateFlow()

    val collections: StateFlow<List<Collection>> = repository.observeAllCollections()
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

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val queryState: StateFlow<LibraryUiState> = combine(
        _searchQuery.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS },
        _selectedStatus,
        _sortOrder,
        _selectedCollectionId,
        _groupBy,
    ) { query, status, sort, collectionId, group ->
        FilterParams(query, status, sort, collectionId) to group
    }.flatMapLatest { (params, group) ->
        val source: Flow<List<Game>> = when {
            params.query.isNotBlank() -> repository.searchGames(escapeLikeQuery(params.query)).map { games ->
                games.filter { game ->
                    (params.status == GameStatusFilter.ALL || game.status.name == params.status.name) &&
                        (params.collectionId == null || game.collections.any { it.id == params.collectionId })
                }
            }
            params.collectionId != null -> repository.observeGamesInCollection(params.collectionId)
            params.status == GameStatusFilter.ALL -> repository.observeAllGames()
            else -> repository.observeGamesByStatus(
                com.gamevault.app.domain.model.GameStatus.valueOf(params.status.name)
            )
        }
        source.map { games -> sortGames(games, params.sort) }
            .map { sortedGames ->
                LibraryUiState(
                    games = sortedGames,
                    searchQuery = _searchQuery.value,
                    isLoading = false,
                    selectedStatus = _selectedStatus.value,
                    sortOrder = _sortOrder.value,
                    selectedCollectionId = _selectedCollectionId.value,
                    groupBy = _groupBy.value,
                )
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(isLoading = true),
    )

    val uiState: StateFlow<LibraryUiState> = combine(
        queryState,
        _gridMode,
        appSettings.showEngineSource,
        appSettings.showStatus,
    ) { state, mode, showEngineSource, showStatus ->
        state.copy(
            gridMode = mode,
            showEngineSource = showEngineSource,
            showStatus = showStatus,
        )
    }.stateIn(
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

    fun onGroupByChanged(group: GroupBy) { _groupBy.value = group }

    fun onGridModeChanged(mode: GridMode) {
        _gridMode.value = mode
        viewModelScope.launch { appSettings.setGridMode(mode) }
    }

    fun onShowEngineSourceChanged(value: Boolean) {
        viewModelScope.launch { appSettings.setShowEngineSource(value) }
    }

    fun onShowStatusChanged(value: Boolean) {
        viewModelScope.launch { appSettings.setShowStatus(value) }
    }

    fun deleteGame(gameId: Long) {
        viewModelScope.launch { repository.deleteGame(gameId) }
    }

    fun updateGameStatusBulk(gameIds: List<Long>, status: com.gamevault.app.domain.model.GameStatus) {
        viewModelScope.launch { repository.updateGameStatusBulk(gameIds, status) }
    }

    fun addGamesToCollection(gameIds: List<Long>, collectionId: Long) {
        viewModelScope.launch { repository.addGamesToCollection(gameIds, collectionId) }
    }

    fun deleteGames(gameIds: List<Long>) {
        viewModelScope.launch { repository.deleteGames(gameIds) }
    }

    // ── Grouping ────────────────────────────────────────────

    /**
     * Compute display items from the current games and groupBy setting.
     * Safe to call from a `remember` or derived state.
     */
    fun computeDisplayItems(games: List<Game>, group: GroupBy): List<DisplayItem> {
        if (group == GroupBy.NONE || games.isEmpty()) {
            return games.map { DisplayItem.GameItem(it) }
        }

        return when (group) {
            GroupBy.STATUS -> {
                games.groupBy { it.status.displayName }.entries.map { (label, grouped) ->
                    listOf(DisplayItem.Header(label, grouped.size)) +
                        grouped.map { DisplayItem.GameItem(it, label) }
                }.flatten()
            }
            GroupBy.SOURCE -> {
                games.groupBy { it.sourceType.displayName }.entries.map { (label, grouped) ->
                    listOf(DisplayItem.Header(label, grouped.size)) +
                        grouped.map { DisplayItem.GameItem(it, label) }
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
