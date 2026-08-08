package com.gamevault.app.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

data class CollectionsUiState(
    val collections: List<CollectionWithCount> = emptyList(),
    val showCreateDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
    val renameTarget: Collection? = null,
    val newCollectionName: String = "",
)

class CollectionsViewModel(
    private val repository: GameRepository,
) : ViewModel() {

    private val _showCreateDialog = MutableStateFlow(false)
    private val _showRenameDialog = MutableStateFlow(false)
    private val _renameTarget = MutableStateFlow<Collection?>(null)
    private val _newCollectionName = MutableStateFlow("")

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

    val uiState: StateFlow<CollectionsUiState> = combine(
        collectionsWithCount,
        _showCreateDialog,
        _showRenameDialog,
        _renameTarget,
        _newCollectionName,
    ) { collections, showCreate, showRename, renameTarget, newName ->
        CollectionsUiState(
            collections = collections,
            showCreateDialog = showCreate,
            showRenameDialog = showRename,
            renameTarget = renameTarget,
            newCollectionName = newName,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CollectionsUiState(),
    )

    fun onNewCollectionNameChange(name: String) {
        _newCollectionName.value = name
    }

    fun showCreateDialog() {
        _showCreateDialog.value = true
        _newCollectionName.value = ""
    }

    fun dismissCreateDialog() {
        _showCreateDialog.value = false
        _newCollectionName.value = ""
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

    class Factory(private val repository: GameRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CollectionsViewModel(repository) as T
    }
}
