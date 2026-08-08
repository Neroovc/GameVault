package com.gamevault.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.domain.repository.GameRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val totalGames: Int = 0,
    val completedCount: Int = 0,
    val playingCount: Int = 0,
    val averageRating: Float? = null,
    val totalSessions: Int = 0,
    val totalPlayTimeMinutes: Long = 0,
    val longestSessionMinutes: Long = 0,
    val mostPlayedTitle: String? = null,
    val mostPlayedMinutes: Long = 0,
    val sourceCounts: List<Pair<String, Int>> = emptyList(),
)

class StatisticsViewModel(
    repository: GameRepository,
) : ViewModel() {

    val uiState: StateFlow<StatisticsUiState> = combine(
        repository.observeAllGames(),
        repository.observeAllSessions(),
    ) { games, sessions ->
        val gameMap = games.associateBy { it.id }

        // ── Library ────────────────────────────────────────
        val totalGames = games.size
        val completedCount = games.count { it.status == GameStatus.COMPLETED }
        val playingCount = games.count { it.status.name == "PLAYING" }
        val rated = games.mapNotNull { it.personalRating }
        val averageRating = if (rated.isEmpty()) null else rated.average().toFloat()

        // ── Play time (only finished sessions count) ───────
        val finished = sessions.filter { it.endTime != null }
        val totalSessions = finished.size
        val totalPlayTimeMinutes = finished.sumOf { it.durationMinutes ?: 0L }
        val longestSessionMinutes = finished.maxOfOrNull { it.durationMinutes ?: 0L } ?: 0L

        val minutesByGame = finished
            .groupBy { it.gameId }
            .mapValues { (_, group) -> group.sumOf { it.durationMinutes ?: 0L } }
        val mostPlayed = minutesByGame.maxByOrNull { it.value }
        val mostPlayedTitle = mostPlayed?.let { (gameId, _) -> gameMap[gameId]?.title }
        val mostPlayedMinutes = mostPlayed?.value ?: 0L

        // ── Sources ────────────────────────────────────────
        val sourceCounts = games
            .groupBy { it.sourceType }
            .mapValues { (_, group) -> group.size }
            .entries
            .sortedByDescending { it.value }
            .map { (type, count) -> type.displayName to count }
            .filter { (_, count) -> count > 0 }

        StatisticsUiState(
            isLoading = false,
            totalGames = totalGames,
            completedCount = completedCount,
            playingCount = playingCount,
            averageRating = averageRating,
            totalSessions = totalSessions,
            totalPlayTimeMinutes = totalPlayTimeMinutes,
            longestSessionMinutes = longestSessionMinutes,
            mostPlayedTitle = mostPlayedTitle,
            mostPlayedMinutes = mostPlayedMinutes,
            sourceCounts = sourceCounts,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatisticsUiState(),
    )

    class Factory(
        private val repository: GameRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StatisticsViewModel(repository) as T
    }
}