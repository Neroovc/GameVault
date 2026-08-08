package com.gamevault.app.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamevault.app.data.settings.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SecurityUiState(
    val cookieSaved: Boolean = false,
    val incognitoMode: Boolean = false,
)

class SecurityViewModel(
    private val appSettings: AppSettings,
) : ViewModel() {

    val uiState: StateFlow<SecurityUiState> = combine(
        appSettings.f95zoneCookie,
        appSettings.incognitoMode,
    ) { cookie, incognito ->
        SecurityUiState(
            cookieSaved = cookie != null && cookie.isNotBlank(),
            incognitoMode = incognito,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SecurityUiState(),
    )

    fun clearCookie() {
        viewModelScope.launch {
            appSettings.setF95zoneCookie(null)
        }
    }

    fun setIncognitoMode(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.setIncognitoMode(enabled)
        }
    }

    class Factory(
        private val appSettings: AppSettings,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SecurityViewModel(appSettings) as T
    }
}