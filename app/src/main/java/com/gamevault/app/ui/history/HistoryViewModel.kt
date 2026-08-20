package com.gamevault.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.PlaySession
import com.gamevault.app.domain.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistorySessionItem(
    val sessionId: Long,
    val startTime: Long,
    val endTime: Long?,
    val durationMinutes: Long?,
    val routeName: String?,
)

data class HistoryItem(
    val gameId: Long,
    val gameTitle: String,
    val coverUrl: String?,
    val lastPlayed: Long,
    val totalSessions: Int,
    val totalPlayTimeMinutes: Long,
    val sessions: List<HistorySessionItem>,
)

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val isLoading: Boolean = true,
)

class HistoryViewModel(
    private val repository: GameRepository,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.observeAllSessions(),
        repository.observeAllGames(),
        repository.observeAllRoutes(),
    ) { sessions, games, routes ->
        val gameMap = games.associateBy { it.id }
        val routeMap = routes.associateBy { it.id }
        val grouped = sessions
            .groupBy { it.gameId }
            .map { (gameId, gameSessions) ->
                val game = gameMap[gameId]
                HistoryItem(
                    gameId = gameId,
                    gameTitle = game?.title ?: "Unknown Game",
                    coverUrl = game?.coverUrl,
                    lastPlayed = gameSessions.maxOf { it.startTime },
                    totalSessions = gameSessions.size,
                    totalPlayTimeMinutes = gameSessions.sumOf { it.durationMinutes ?: 0L },
                    sessions = gameSessions
                        .sortedByDescending { it.startTime }
                        .map { session ->
                            HistorySessionItem(
                                sessionId = session.id,
                                startTime = session.startTime,
                                endTime = session.endTime,
                                durationMinutes = session.durationMinutes,
                                routeName = session.routeId?.let { routeMap[it]?.name },
                            )
                        },
                )
            }
            .sortedByDescending { it.lastPlayed }
        HistoryUiState(items = grouped, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(isLoading = true),
    )

    class Factory(
        private val repository: GameRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HistoryViewModel(repository) as T
    }
}
