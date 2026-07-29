package com.gamevault.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.repository.GameRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val games: List<Game> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val selectedStatus: GameStatusFilter = GameStatusFilter.ALL,
    val sortOrder: SortOrder = SortOrder.DATE_ADDED_DESC,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> = combine(
        _searchQuery,
        _selectedStatus,
        _sortOrder,
    ) { query, status, sort ->
        Triple(query, status, sort)
    }.flatMapLatest { (query, status, sort) ->
        val source = when {
            query.isNotBlank() -> repository.searchGames(query)
            status != GameStatusFilter.ALL -> {
                val gameStatus = when (status) {
                    GameStatusFilter.NOT_STARTED -> com.gamevault.app.domain.model.GameStatus.NOT_STARTED
                    GameStatusFilter.PLAYING -> com.gamevault.app.domain.model.GameStatus.PLAYING
                    GameStatusFilter.COMPLETED -> com.gamevault.app.domain.model.GameStatus.COMPLETED
                    GameStatusFilter.REPLAYING -> com.gamevault.app.domain.model.GameStatus.REPLAYING
                    GameStatusFilter.PAUSED -> com.gamevault.app.domain.model.GameStatus.PAUSED
                    GameStatusFilter.ABANDONED -> com.gamevault.app.domain.model.GameStatus.ABANDONED
                    else -> repository.observeAllGames()
                }
                repository.observeGamesByStatus(gameStatus)
            }
            else -> repository.observeAllGames()
        }
        source
    }.combine(
        combine(_searchQuery, _selectedStatus, _sortOrder) { q, s, o -> Triple(q, s, o) }
    ) { games, _ ->
        val sorted = sortGames(games, _sortOrder.value)
        LibraryUiState(
            games = sorted,
            searchQuery = _searchQuery.value,
            isLoading = false,
            selectedStatus = _selectedStatus.value,
            sortOrder = _sortOrder.value,
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

    fun deleteGame(gameId: Long) {
        viewModelScope.launch {
            repository.deleteGame(gameId)
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
