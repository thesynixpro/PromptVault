package com.promptvault.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptvault.AppContainer
import com.promptvault.domain.Category
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryUiState(
    val categories: List<Category> = emptyList()
)

class CategoriesViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository

    val uiState: StateFlow<CategoryUiState> = repository.observeCategories()
        .map { CategoryUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryUiState())

    fun create(name: String, color: Long, icon: String) = viewModelScope.launch {
        repository.createCategory(name, color, icon)
    }

    fun update(id: String, name: String, color: Long, icon: String) = viewModelScope.launch {
        repository.updateCategory(id, name, color, icon)
    }

    fun delete(id: String) = viewModelScope.launch {
        repository.deleteCategory(id)
    }
}
