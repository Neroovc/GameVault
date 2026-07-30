package com.gamevault.app.ui.library

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LibrarySelectionViewModel : ViewModel() {

    private val _selectedGameIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedGameIds: StateFlow<Set<Long>> = _selectedGameIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedGameIds
        .map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val selectedCount: StateFlow<Int> = _selectedGameIds
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    fun toggleSelection(gameId: Long) {
        _selectedGameIds.value = _selectedGameIds.value.toMutableSet().also { set ->
            if (set.contains(gameId)) set.remove(gameId) else set.add(gameId)
        }
    }

    fun selectAll(gameIds: List<Long>) {
        _selectedGameIds.value = gameIds.toSet()
    }

    fun clearSelection() {
        _selectedGameIds.value = emptySet()
    }

    fun isSelected(gameId: Long): Boolean = gameId in _selectedGameIds.value
}
