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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CollectionWithCount(
    val collection: Collection,
    val gameCount: Int,
)

data class CollectionsUiState(
    val collections: List<CollectionWithCount> = emptyList(),
    val isLoading: Boolean = true,
    val showCreateDialog: Boolean = false,
    val newCollectionName: String = "",
    val newCollectionDescription: String = "",
)

class CollectionsViewModel(
    private val repository: GameRepository,
) : ViewModel() {

    private val _showCreateDialog = MutableStateFlow(false)
    private val _newName = MutableStateFlow("")
    private val _newDescription = MutableStateFlow("")

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
        _newName,
        _newDescription,
    ) { collections, showDlg, name, desc ->
        CollectionsUiState(
            collections = collections,
            isLoading = false,
            showCreateDialog = showDlg,
            newCollectionName = name,
            newCollectionDescription = desc,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CollectionsUiState(isLoading = true),
    )

    fun showCreateDialog() {
        _showCreateDialog.value = true
    }

    fun dismissCreateDialog() {
        _showCreateDialog.value = false
        _newName.value = ""
        _newDescription.value = ""
    }

    fun setNewName(name: String) { _newName.value = name }
    fun setNewDescription(desc: String) { _newDescription.value = desc }

    fun createCollection() {
        val name = _newName.value.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.saveCollection(
                Collection(name = name, description = _newDescription.value.ifBlank { null })
            )
            dismissCreateDialog()
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
