package com.gamevault.app.ui.datastorage

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamevault.app.data.backup.AutoBackupWorker
import com.gamevault.app.data.local.BackupOptions
import com.gamevault.app.data.local.GameVaultBackup
import com.gamevault.app.data.local.RestoreOptions
import com.gamevault.app.data.settings.AppSettings
import com.gamevault.app.data.settings.AutoBackupFrequency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class StorageStats(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
)

data class DataStorageUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val lastBackupResult: String? = null,
    val storage: StorageStats? = null,
    val autoBackupEnabled: Boolean = false,
    val autoBackupFrequency: AutoBackupFrequency = AutoBackupFrequency.DAILY,
    val autoBackupKeepCount: Int = 5,
    val autoBackupDirPath: String? = null,
)

class DataStorageViewModel(
    private val backup: GameVaultBackup,
    private val appSettings: AppSettings,
    private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataStorageUiState())
    val uiState: StateFlow<DataStorageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val stat = StatFs(context.filesDir.path)
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes
            _uiState.value = _uiState.value.copy(
                storage = StorageStats(
                    totalBytes = totalBytes,
                    usedBytes = totalBytes - availableBytes,
                    availableBytes = availableBytes,
                ),
            )
        }
        viewModelScope.launch {
            combine(
                appSettings.autoBackupEnabled,
                appSettings.autoBackupFrequency,
                appSettings.autoBackupKeepCount,
            ) { enabled, frequency, keepCount ->
                Triple(enabled, frequency, keepCount)
            }.collect { (enabled, frequency, keepCount) ->
                _uiState.value = _uiState.value.copy(
                    autoBackupEnabled = enabled,
                    autoBackupFrequency = frequency,
                    autoBackupKeepCount = keepCount,
                )
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val path = AutoBackupWorker.backupDir(context)?.absolutePath
            _uiState.value = _uiState.value.copy(autoBackupDirPath = path)
        }
    }

    fun exportBackup(context: Context, uri: Uri, options: BackupOptions = BackupOptions.ALL) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)
            try {
                backup.exportToFile(context, uri, options)
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    lastBackupResult = "Backup exported successfully",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    lastBackupResult = "Export failed: ${e.message}",
                )
            }
        }
    }

    fun importBackup(
        context: Context,
        uri: Uri,
        options: RestoreOptions = RestoreOptions.ALL,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            try {
                val result = backup.importFromFile(context, uri, options)
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    lastBackupResult = if (result.success) {
                        buildString {
                            append("Imported ${result.gamesImported} games, ${result.collectionsImported} collections")
                            if (result.settingsImported) append(", settings restored")
                        }
                    } else {
                        result.message
                    },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    lastBackupResult = "Import failed: ${e.message}",
                )
            }
        }
    }

    fun clearBackupResult() {
        _uiState.value = _uiState.value.copy(lastBackupResult = null)
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.setAutoBackupEnabled(enabled)
            // Apply immediately — do not wait for the next process restart.
            if (enabled) {
                AutoBackupWorker.schedule(
                    context,
                    appSettings.autoBackupFrequency.first().intervalDays,
                )
            } else {
                AutoBackupWorker.cancel(context)
            }
        }
    }

    fun setAutoBackupFrequency(frequency: AutoBackupFrequency) {
        viewModelScope.launch {
            appSettings.setAutoBackupFrequency(frequency)
            if (appSettings.autoBackupEnabled.first()) {
                AutoBackupWorker.schedule(context, frequency.intervalDays)
            }
        }
    }

    fun setAutoBackupKeepCount(count: Int) {
        viewModelScope.launch {
            appSettings.setAutoBackupKeepCount(count)
        }
    }

    class Factory(
        private val backup: GameVaultBackup,
        private val appSettings: AppSettings,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DataStorageViewModel(backup, appSettings, context) as T
    }
}