package com.gamevault.app.ui.datastorage

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamevault.app.data.local.BackupOptions
import com.gamevault.app.data.local.GameVaultBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

class DataStorageViewModel(
    private val backup: GameVaultBackup,
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

    fun importBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            try {
                val result = backup.importFromFile(context, uri)
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

    class Factory(
        private val backup: GameVaultBackup,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DataStorageViewModel(backup, context) as T
    }
}