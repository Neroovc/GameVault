package com.gamevault.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamevault.app.domain.model.Collection
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.repository.GameRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val games: List<Game> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val selectedStatus: GameStatusFilter = GameStatusFilter.ALL,
    val sortOrder: SortOrder = SortOrder.DATE_ADDED_DESC,
    val selectedCollectionId: Long? = null,
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

class LibraryViewModel(
    private val repository: GameRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedStatus = MutableStateFlow(GameStatusFilter.ALL)
    val selectedStatus: StateFlow<GameStatusFilter> = _selectedStatus.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _selectedCollectionId = MutableStateFlow<Long?>(null)
    val selectedCollectionId: StateFlow<Long?> = _selectedCollectionId.asStateFlow()

    val collections: StateFlow<List<Collection>> = repository.observeAllCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class FilterParams(
        val query: String,
        val status: GameStatusFilter,
        val sort: SortOrder,
        val collectionId: Long?,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> = combine(
        _searchQuery,
        _selectedStatus,
        _sortOrder,
        _selectedCollectionId,
    ) { query, status, sort, collectionId ->
        FilterParams(query, status, sort, collectionId)
    }.flatMapLatest { (query, status, sort, collectionId) ->
        val source: Flow<List<Game>> = when {
            query.isNotBlank() -> repository.searchGames(query)
            collectionId != null -> repository.observeGamesInCollection(collectionId)
            status == GameStatusFilter.ALL -> repository.observeAllGames()
            else -> repository.observeGamesByStatus(
                com.gamevault.app.domain.model.GameStatus.valueOf(status.name)
            )
        }
        source.map { games -> sortGames(games, sort) }
    }.map { sortedGames ->
        LibraryUiState(
            games = sortedGames,
            searchQuery = _searchQuery.value,
            isLoading = false,
            selectedStatus = _selectedStatus.value,
            sortOrder = _sortOrder.value,
            selectedCollectionId = _selectedCollectionId.value,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(isLoading = true),
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onStatusFilterChanged(status: GameStatusFilter) {
        _selectedStatus.value = status
    }

    fun onSortOrderChanged(order: SortOrder) {
        _sortOrder.value = order
    }

    fun onCollectionFilterChanged(collectionId: Long?) {
        _selectedCollectionId.value = collectionId
    }

    fun deleteGame(gameId: Long) {
        viewModelScope.launch {
            repository.deleteGame(gameId)
        }
    }

    fun updateGameStatusBulk(gameIds: List<Long>, status: com.gamevault.app.domain.model.GameStatus) {
        viewModelScope.launch {
            repository.updateGameStatusBulk(gameIds, status)
        }
    }

    fun addGamesToCollection(gameIds: List<Long>, collectionId: Long) {
        viewModelScope.launch {
            repository.addGamesToCollection(gameIds, collectionId)
        }
    }

    fun deleteGames(gameIds: List<Long>) {
        viewModelScope.launch {
            repository.deleteGames(gameIds)
        }
    }

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

    class Factory(private val repository: GameRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryViewModel(repository) as T
    }
}
