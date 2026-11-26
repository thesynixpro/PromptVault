package com.promptvault.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptvault.AppContainer
import com.promptvault.domain.Folder
import com.promptvault.domain.ItemFilter
import com.promptvault.domain.PromptItem
import com.promptvault.domain.SearchEngine
import com.promptvault.domain.SortMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<PromptItem> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val recent: List<String> = emptyList(),
    val filter: ItemFilter = ItemFilter.ALL,
    val sort: SortMode = SortMode.RECENTLY_MODIFIED,
    val folders: List<Folder> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val settingsStore = container.settings
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(ItemFilter.ALL)
    private val sort = MutableStateFlow(SortMode.RECENTLY_MODIFIED)

    init {
        viewModelScope.launch {
            sort.value = settingsStore.settings.first().sortMode
        }
    }

    private val results = combine(query.debounce(220), filter, sort) { q, f, s -> Triple(q, f, s) }
        .flatMapLatest { (q, f, s) -> repository.observeItems(f, s, q) }

    val uiState: StateFlow<SearchUiState> = combine(
        combine(results, repository.observeSearchHistory(), query, filter, sort) { items, recent, q, f, s ->
            SearchUiState(
                query = q,
                results = items,
                suggestions = SearchEngine.suggestions(recent, q),
                recent = recent,
                filter = f,
                sort = s
            )
        },
        repository.observeFolders()
    ) { partial, folders ->
        partial.copy(folders = folders)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setFilter(value: ItemFilter) {
        filter.value = value
    }

    fun setSort(value: SortMode) {
        sort.value = value
    }

    fun commitSearch() = viewModelScope.launch {
        repository.addSearchQuery(query.value)
    }

    fun clearHistory() = viewModelScope.launch {
        repository.clearSearchHistory()
    }

    fun toggleFavorite(id: String) = viewModelScope.launch {
        repository.toggleFavorite(id)
    }

    fun duplicateItem(id: String) = viewModelScope.launch {
        repository.duplicateItem(id)
    }

    fun deleteItem(id: String, cloudDelete: Boolean) = viewModelScope.launch {
        repository.deleteItem(id, cloudDelete)
    }

    fun moveItem(id: String, folderId: String?) = viewModelScope.launch {
        repository.moveItem(id, folderId)
    }
}
