package com.gamevault.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamevault.app.domain.model.Collection
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
    val allCollections: List<Collection> = emptyList(),
    val gameCollections: List<Collection> = emptyList(),
    val showCollectionPicker: Boolean = false,
    val showEditNotes: Boolean = false,
    val editNotesText: String = "",
)

class GameDetailViewModel(
    private val gameId: Long,
    private val repository: GameRepository,
) : ViewModel() {

    private val _showCollectionPicker = MutableStateFlow(false)
    private val _showEditNotes = MutableStateFlow(false)
    private val _editNotesText = MutableStateFlow("")

    val uiState: StateFlow<GameDetailUiState> = combine(
        repository.observeGameById(gameId),
        repository.observeRoutesForGame(gameId),
        repository.observeSessionsForGame(gameId),
        repository.observeAllCollections(),
        repository.observeGameCollections(gameId),
        _showCollectionPicker,
        _showEditNotes,
        _editNotesText,
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        GameDetailUiState(
            game = array[0] as Game?,
            routes = array[1] as List<GameRoute>,
            sessions = array[2] as List<PlaySession>,
            totalPlayTime = (array[2] as List<PlaySession>).sumOf { it.durationMinutes ?: 0L },
            isLoading = false,
            allCollections = array[3] as List<Collection>,
            gameCollections = array[4] as List<Collection>,
            showCollectionPicker = array[5] as Boolean,
            showEditNotes = array[6] as Boolean,
            editNotesText = array[7] as String,
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
            dismissEditNotes()
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

    fun setManualPlayTime(minutes: Long) {
        viewModelScope.launch {
            repository.updateGamePlayTime(gameId, minutes)
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
            val sessions = uiState.value.sessions
            val session = sessions.find { it.id == sessionId } ?: return@launch
            val now = System.currentTimeMillis()
            val duration = (now - session.startTime) / 60_000
            val ended = session.copy(endTime = now, durationMinutes = duration)
            repository.updateSession(ended)

            val updatedSessions = sessions.map { if (it.id == sessionId) ended else it }
            val newTotal = updatedSessions.sumOf { it.durationMinutes ?: 0L }
            repository.updateGamePlayTime(gameId, newTotal)
        }
    }

    // ── Collection Picker ───────────────────────────────────

    fun showCollectionPicker() {
        _showCollectionPicker.value = true
    }

    fun dismissCollectionPicker() {
        _showCollectionPicker.value = false
    }

    fun toggleGameCollection(collectionId: Long, currentlyInCollection: Boolean) {
        viewModelScope.launch {
            if (currentlyInCollection) {
                repository.removeGameFromCollection(gameId, collectionId)
            } else {
                repository.addGameToCollection(gameId, collectionId)
            }
        }
    }

    // ── Delete / Remove from Library ───────────────────────

    fun deleteGame(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteGame(gameId)
            onDone()
        }
    }

    // ── Edit Notes ──────────────────────────────────────────

    fun showEditNotes() {
        _editNotesText.value = uiState.value.game?.notes ?: ""
        _showEditNotes.value = true
    }

    fun setEditNotesText(text: String) {
        _editNotesText.value = text
    }

    fun dismissEditNotes() {
        _showEditNotes.value = false
        _editNotesText.value = ""
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
