package com.promptvault.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptvault.AppContainer
import com.promptvault.domain.Category
import com.promptvault.domain.Folder
import com.promptvault.domain.ItemFilter
import com.promptvault.domain.PromptItem
import com.promptvault.domain.SortMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListUiState(
    val items: List<PromptItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val filter: ItemFilter = ItemFilter.ALL,
    val sort: SortMode = SortMode.RECENTLY_MODIFIED
)

@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModel(container: AppContainer, initialFilter: ItemFilter) : ViewModel() {
    private val repository = container.repository
    private val sort = MutableStateFlow(SortMode.RECENTLY_MODIFIED)
    private val filter = MutableStateFlow(initialFilter)

    private val items = combine(filter, sort) { f, s -> f to s }
        .flatMapLatest { (f, s) -> repository.observeItems(f, s) }

    val uiState: StateFlow<ListUiState> = combine(
        items,
        repository.observeCategories(),
        repository.observeFolders(),
        sort,
        filter
    ) { rows, categories, folders, s, f ->
        ListUiState(rows, categories, folders, f, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    fun setSort(value: SortMode) {
        sort.value = value
    }

    fun toggleFavorite(id: String) = viewModelScope.launch { repository.toggleFavorite(id) }

    fun duplicate(id: String) = viewModelScope.launch { repository.duplicateItem(id) }

    fun delete(id: String, cloudDelete: Boolean) = viewModelScope.launch {
        repository.deleteItem(id, cloudDelete)
    }

    fun move(id: String, folderId: String?) = viewModelScope.launch {
        repository.moveItem(id, folderId)
    }
}
