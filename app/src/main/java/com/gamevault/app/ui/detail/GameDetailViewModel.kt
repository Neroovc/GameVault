package com.gamevault.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameRoute
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.domain.model.PlaySession
import com.gamevault.app.domain.model.RouteStatus
import com.gamevault.app.domain.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GameDetailUiState(
    val game: Game? = null,
    val routes: List<GameRoute> = emptyList(),
    val sessions: List<PlaySession> = emptyList(),
    val totalPlayTime: Long = 0,
    val isLoading: Boolean = true,
)

class GameDetailViewModel(
    private val gameId: Long,
    private val repository: GameRepository,
) : ViewModel() {

    val uiState: StateFlow<GameDetailUiState> = combine(
        repository.observeGameById(gameId),
        repository.observeRoutesForGame(gameId),
        repository.observeSessionsForGame(gameId),
    ) { game, routes, sessions ->
        GameDetailUiState(
            game = game,
            routes = routes,
            sessions = sessions,
            totalPlayTime = sessions.sumOf { it.durationMinutes ?: 0L },
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GameDetailUiState(isLoading = true),
    )

    fun updateGameStatus(status: GameStatus) {
        viewModelScope.launch {
            val current = uiState.value.game ?: return@launch
            repository.updateGame(current.copy(status = status))
        }
    }

    fun updatePersonalRating(rating: Float) {
        viewModelScope.launch {
            val current = uiState.value.game ?: return@launch
            repository.updateGame(current.copy(personalRating = rating))
        }
    }

    fun updateNotes(notes: String) {
        viewModelScope.launch {
            val current = uiState.value.game ?: return@launch
            repository.updateGame(current.copy(notes = notes))
        }
    }

    fun updateRouteProgress(routeId: Long, progress: Int) {
        viewModelScope.launch {
            val route = uiState.value.routes.find { it.id == routeId } ?: return@launch
            val newStatus = when {
                progress >= 100 -> RouteStatus.COMPLETED
                progress > 0 -> RouteStatus.IN_PROGRESS
                else -> RouteStatus.UNLOCKED
            }
            repository.updateRoute(
                route.copy(progress = progress, status = newStatus)
            )
        }
    }

    fun addRoute(name: String) {
        viewModelScope.launch {
            val routes = uiState.value.routes
            repository.saveRoute(
                GameRoute(
                    gameId = gameId,
                    name = name,
                    order = routes.size,
                    status = RouteStatus.UNLOCKED,
                )
            )
        }
    }

    fun deleteRoute(route: GameRoute) {
        viewModelScope.launch {
            repository.deleteRoute(route)
        }
    }

    fun startPlaySession() {
        viewModelScope.launch {
            repository.saveSession(
                PlaySession(
                    gameId = gameId,
                    startTime = System.currentTimeMillis(),
                )
            )
        }
    }

    fun endPlaySession(sessionId: Long) {
        viewModelScope.launch {
            val session = uiState.value.sessions.find { it.id == sessionId } ?: return@launch
            val now = System.currentTimeMillis()
            val duration = (now - session.startTime) / 60_000
            repository.updateSession(
                session.copy(
                    endTime = now,
                    durationMinutes = duration,
                )
            )
        }
    }

    class Factory(
        private val gameId: Long,
        private val repository: GameRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GameDetailViewModel(gameId, repository) as T
    }
}
