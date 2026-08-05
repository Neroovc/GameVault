package com.gamevault.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamevault.app.data.local.GameVaultBackup
import com.gamevault.app.data.settings.AppSettings
import com.gamevault.app.data.settings.SourceRequestPace
import com.gamevault.app.data.settings.ThemeMode
import com.gamevault.app.domain.model.Collection
import com.gamevault.app.domain.repository.GameRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CollectionWithCount(
    val collection: Collection,
    val gameCount: Int,
)

data class BackupUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val lastBackupResult: String? = null,
)

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledDark: Boolean = false,
    val gifAutoplay: Boolean = true,
    val collections: List<CollectionWithCount> = emptyList(),
    val showCreateDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
    val renameTarget: Collection? = null,
    val newCollectionName: String = "",
    val backupUi: BackupUiState = BackupUiState(),
    val defaultCollectionId: Long? = null,
    val sourceRequestPace: SourceRequestPace = SourceRequestPace.GENTLE,
)

class SettingsViewModel(
    private val appSettings: AppSettings,
    private val repository: GameRepository,
    private val backupManager: GameVaultBackup,
) : ViewModel() {

    private val _showCreateDialog = MutableStateFlow(false)
    private val _showRenameDialog = MutableStateFlow(false)
    private val _renameTarget = MutableStateFlow<Collection?>(null)
    private val _newCollectionName = MutableStateFlow("")
    private val _backupUi = MutableStateFlow(BackupUiState())

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
        appSettings.amoledDark,
        appSettings.gifAutoplay,
        collectionsWithCount,
        _showCreateDialog,
        _showRenameDialog,
        _renameTarget,
        _newCollectionName,
        _backupUi,
        appSettings.defaultCollectionId,
        appSettings.sourceRequestPace,
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        SettingsUiState(
            themeMode = array[0] as ThemeMode,
            amoledDark = array[1] as Boolean,
            gifAutoplay = array[2] as Boolean,
            collections = array[3] as List<CollectionWithCount>,
            showCreateDialog = array[4] as Boolean,
            showRenameDialog = array[5] as Boolean,
            renameTarget = array[6] as Collection?,
            newCollectionName = array[7] as String,
            backupUi = array[8] as BackupUiState,
            defaultCollectionId = array[9] as Long?,
            sourceRequestPace = array[10] as SourceRequestPace,
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

    fun setDefaultCollectionId(collectionId: Long?) {
        viewModelScope.launch {
            appSettings.setDefaultCollectionId(collectionId)
        }
    }

    fun showCreateDialog() {
        _showCreateDialog.value = true
        _newCollectionName.value = ""
    }

    fun dismissCreateDialog() {
        _showCreateDialog.value = false
        _newCollectionName.value = ""
    }

    fun setNewCollectionName(name: String) {
        _newCollectionName.value = name
    }

    fun createCollection() {
        val name = _newCollectionName.value.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.saveCollection(Collection(name = name))
            dismissCreateDialog()
        }
    }

    fun showRenameDialog(collection: Collection) {
        _renameTarget.value = collection
        _newCollectionName.value = collection.name
        _showRenameDialog.value = true
    }

    fun dismissRenameDialog() {
        _showRenameDialog.value = false
        _renameTarget.value = null
        _newCollectionName.value = ""
    }

    fun renameCollection() {
        val target = _renameTarget.value ?: return
        val name = _newCollectionName.value.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.updateCollection(target.copy(name = name))
            dismissRenameDialog()
        }
    }

    fun deleteCollection(collection: Collection) {
        viewModelScope.launch {
            repository.deleteCollection(collection)
        }
    }

    fun exportBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            _backupUi.value = _backupUi.value.copy(isExporting = true)
            try {
                backupManager.exportToFile(context, uri)
                _backupUi.value = _backupUi.value.copy(
                    isExporting = false,
                    lastBackupResult = "Backup exported successfully",
                )
            } catch (e: Exception) {
                _backupUi.value = _backupUi.value.copy(
                    isExporting = false,
                    lastBackupResult = "Export failed: ${e.message}",
                )
            }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            _backupUi.value = _backupUi.value.copy(isImporting = true)
            try {
                val result = backupManager.importFromFile(context, uri)
                _backupUi.value = _backupUi.value.copy(
                    isImporting = false,
                    lastBackupResult = if (result.success) {
                        "Imported ${result.gamesImported} games, ${result.collectionsImported} collections"
                    } else {
                        "Import failed: ${result.message}"
                    },
                )
            } catch (e: Exception) {
                _backupUi.value = _backupUi.value.copy(
                    isImporting = false,
                    lastBackupResult = "Import failed: ${e.message}",
                )
            }
        }
    }

    fun clearBackupResult() {
        _backupUi.value = _backupUi.value.copy(lastBackupResult = null)
    }

    class Factory(
        private val appSettings: AppSettings,
        private val repository: GameRepository,
        private val backup: GameVaultBackup,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(appSettings, repository, backup) as T
    }
}
