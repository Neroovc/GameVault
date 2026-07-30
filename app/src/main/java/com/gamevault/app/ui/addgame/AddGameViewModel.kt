package com.gamevault.app.ui.addgame

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamevault.app.data.remote.F95ZoneScraper
import com.gamevault.app.data.remote.ScrapeResult
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameEngine
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.domain.model.SourceType
import com.gamevault.app.domain.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddGameUiState(
    val activeTab: Int = 0,
    val url: String = "",
    val isScraping: Boolean = false,
    val scrapeError: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    // Form fields
    val title: String = "",
    val coverUrl: String = "",
    val developer: String = "",
    val engine: GameEngine? = null,
    val version: String = "",
    val description: String = "",
    val notes: String = "",
    val status: GameStatus = GameStatus.NOT_STARTED,
    val sourceType: SourceType = SourceType.F95ZONE,
    val sourceUrl: String = "",
    val f95Url: String = "",
)

class AddGameViewModel(
    private val gameRepository: GameRepository,
    private val scraper: F95ZoneScraper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddGameUiState())
    val uiState: StateFlow<AddGameUiState> = _uiState.asStateFlow()

    fun setActiveTab(tab: Int) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setUrl(url: String) {
        _uiState.update { it.copy(url = url) }
    }

    fun fetchFromUrl() {
        val url = _uiState.value.url.trim()
        if (url.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isScraping = true, scrapeError = null) }
            when (val result = scraper.scrapeGame(url)) {
                is ScrapeResult.Success -> {
                    val game = result.game
                    _uiState.update {
                        it.copy(
                            isScraping = false,
                            title = game.title,
                            coverUrl = game.coverUrl ?: "",
                            developer = game.developer ?: "",
                            engine = game.engine,
                            version = game.version ?: "",
                            description = game.description ?: "",
                            f95Url = game.f95Url ?: url,
                            sourceUrl = url,
                            sourceType = SourceType.F95ZONE,
                        )
                    }
                }
                is ScrapeResult.Error -> {
                    _uiState.update {
                        it.copy(isScraping = false, scrapeError = result.message)
                    }
                }
            }
        }
    }

    // ── Form field updaters ──────────────────────────────

    fun updateTitle(value: String) { _uiState.update { it.copy(title = value) } }
    fun updateCoverUrl(value: String) { _uiState.update { it.copy(coverUrl = value) } }
    fun updateDeveloper(value: String) { _uiState.update { it.copy(developer = value) } }
    fun updateEngine(value: GameEngine?) { _uiState.update { it.copy(engine = value) } }
    fun updateVersion(value: String) { _uiState.update { it.copy(version = value) } }
    fun updateDescription(value: String) { _uiState.update { it.copy(description = value) } }
    fun updateNotes(value: String) { _uiState.update { it.copy(notes = value) } }
    fun updateStatus(value: GameStatus) { _uiState.update { it.copy(status = value) } }
    fun updateSourceType(value: SourceType) { _uiState.update { it.copy(sourceType = value) } }
    fun updateSourceUrl(value: String) { _uiState.update { it.copy(sourceUrl = value) } }

    fun saveGame() {
        val state = _uiState.value
        if (state.title.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val game = Game(
                title = state.title,
                coverUrl = state.coverUrl.ifBlank { null },
                description = state.description.ifBlank { null },
                developer = state.developer.ifBlank { null },
                engine = state.engine,
                version = state.version.ifBlank { null },
                status = state.status,
                notes = state.notes.ifBlank { null },
                dateAdded = System.currentTimeMillis(),
                f95Url = state.f95Url.ifBlank { null },
                sourceType = state.sourceType,
                sourceUrl = state.sourceUrl.ifBlank { null },
            )
            gameRepository.saveGame(game)
            _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
        }
    }

    fun reset() {
        _uiState.value = AddGameUiState()
    }

    class Factory(
        private val gameRepository: GameRepository,
        private val scraper: F95ZoneScraper,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddGameViewModel(gameRepository, scraper) as T
        }
    }
}
