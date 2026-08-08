package com.gamevault.app.ui.advanced

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamevault.app.data.settings.AppSettings
import com.gamevault.app.data.settings.StatusStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdvancedUiState(
    val showEngine: Boolean = true,
    val showSource: Boolean = true,
    val showStatus: Boolean = true,
    val statusStyle: StatusStyle = StatusStyle.TOP_BAR,
)

class AdvancedViewModel(
    private val appSettings: AppSettings,
) : ViewModel() {

    val uiState: StateFlow<AdvancedUiState> = combine(
        appSettings.showEngine,
        appSettings.showSource,
        appSettings.showStatus,
        appSettings.statusStyle,
    ) { engine, source, status, style ->
        AdvancedUiState(
            showEngine = engine,
            showSource = source,
            showStatus = status,
            statusStyle = style,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AdvancedUiState(),
    )

    fun setShowEngine(value: Boolean) {
        viewModelScope.launch { appSettings.setShowEngine(value) }
    }

    fun setShowSource(value: Boolean) {
        viewModelScope.launch { appSettings.setShowSource(value) }
    }

    fun setShowStatus(value: Boolean) {
        viewModelScope.launch { appSettings.setShowStatus(value) }
    }

    fun setStatusStyle(style: StatusStyle) {
        viewModelScope.launch { appSettings.setStatusStyle(style) }
    }

    class Factory(
        private val appSettings: AppSettings,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AdvancedViewModel(appSettings) as T
    }
}