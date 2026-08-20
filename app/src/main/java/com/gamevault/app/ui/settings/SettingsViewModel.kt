package com.gamevault.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamevault.app.data.settings.AppSettings
import com.gamevault.app.data.settings.ColorPalette
import com.gamevault.app.data.settings.SourceRequestPace
import com.gamevault.app.data.settings.ThemeMode
import com.gamevault.app.data.settings.UpdateCheckInterval
import com.gamevault.app.domain.repository.GameRepository
import com.gamevault.app.ui.collections.CollectionWithCount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorPalette: ColorPalette = ColorPalette.VIOLET,
    val amoledDark: Boolean = false,
    val gifAutoplay: Boolean = true,
    val collections: List<CollectionWithCount> = emptyList(),
    val defaultCollectionId: Long? = null,
    val sourceRequestPace: SourceRequestPace = SourceRequestPace.GENTLE,
    val updateCheckInterval: UpdateCheckInterval = UpdateCheckInterval.HOURS_12,
)

class SettingsViewModel(
    private val appSettings: AppSettings,
    private val repository: GameRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val collectionsWithCount: StateFlow<List<CollectionWithCount>> =
        repository.observeAllCollections().flatMapLatest { collections ->
            val counts = collections.map { coll ->
                val count = repository.getGameCountForCollection(coll.id)
                CollectionWithCount(collection = coll, gameCount = count)
            }
            flowOf(counts)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val uiState: StateFlow<SettingsUiState> = combine(
        appSettings.themeMode,
        appSettings.colorPalette,
        appSettings.amoledDark,
        appSettings.gifAutoplay,
        collectionsWithCount,
        appSettings.defaultCollectionId,
        appSettings.sourceRequestPace,
        appSettings.updateCheckInterval,
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        SettingsUiState(
            themeMode = array[0] as ThemeMode,
            colorPalette = array[1] as ColorPalette,
            amoledDark = array[2] as Boolean,
            gifAutoplay = array[3] as Boolean,
            collections = array[4] as List<CollectionWithCount>,
            defaultCollectionId = array[5] as Long?,
            sourceRequestPace = array[6] as SourceRequestPace,
            updateCheckInterval = array[7] as UpdateCheckInterval,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            appSettings.setThemeMode(mode)
        }
    }

    fun setColorPalette(palette: ColorPalette) {
        viewModelScope.launch {
            appSettings.setColorPalette(palette)
        }
    }

    fun setAmoledDark(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.setAmoledDark(enabled)
        }
    }

    fun setGifAutoplay(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.setGifAutoplay(enabled)
        }
    }

    fun setSourceRequestPace(pace: SourceRequestPace) {
        viewModelScope.launch {
            appSettings.setSourceRequestPace(pace)
        }
    }

    fun setUpdateCheckInterval(interval: UpdateCheckInterval) {
        viewModelScope.launch {
            appSettings.setUpdateCheckInterval(interval)
        }
    }

    fun setDefaultCollectionId(collectionId: Long?) {
        viewModelScope.launch {
            appSettings.setDefaultCollectionId(collectionId)
        }
    }

    class Factory(
        private val appSettings: AppSettings,
        private val repository: GameRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(appSettings, repository) as T
    }
}