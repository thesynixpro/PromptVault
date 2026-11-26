package com.promptvault.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptvault.AppContainer
import com.promptvault.domain.Category
import com.promptvault.domain.Folder
import com.promptvault.domain.ItemFilter
import com.promptvault.domain.PromptItem
import com.promptvault.domain.SortMode
import com.promptvault.domain.SyncTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeStats(
    val total: Int = 0,
    val favorites: Int = 0,
    val folders: Int = 0,
    val synced: Int = 0,
    val pending: Int = 0,
    val failed: Int = 0
)

data class HomeUiState(
    val items: List<PromptItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val tasks: List<SyncTask> = emptyList(),
    val stats: HomeStats = HomeStats(),
    val filter: ItemFilter = ItemFilter.RECENT,
    val sort: SortMode = SortMode.RECENTLY_MODIFIED,
    val telegramConnected: Boolean = false,
    val isRefreshing: Boolean = false,
    val message: String? = null
)

private data class HomePartial(
    val items: List<PromptItem>,
    val categories: List<Category>,
    val folders: List<Folder>,
    val tasks: List<SyncTask>,
    val connected: Boolean,
    val stats: HomeStats
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val settingsStore = container.settings
    private val telegram = container.telegram

    private val filter = MutableStateFlow(ItemFilter.RECENT)
    private val sort = MutableStateFlow(SortMode.RECENTLY_MODIFIED)
    private val isRefreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            sort.value = settingsStore.settings.first().sortMode
        }
    }

    private val itemsFlow = combine(filter, sort) { f, s -> f to s }
        .flatMapLatest { (f, s) -> repository.observeItems(f, s) }

    private val contentFlow = combine(
        itemsFlow,
        repository.observeCategories(),
        repository.observeFolders(),
        repository.observeSyncTasks(),
        container.telegram.configured
    ) { items, categories, folders, tasks, connected ->
        HomePartial(items, categories, folders, tasks, connected, HomeStats())
    }

    private val fullFlow = combine(contentFlow, repository.observeStats()) { partial, stats ->
        partial.copy(
            stats = HomeStats(
                total = stats.total,
                favorites = stats.favorites,
                folders = stats.folders,
                synced = stats.synced,
                pending = stats.pending,
                failed = stats.failed
            )
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(fullFlow, isRefreshing, message) { partial, refreshing, status ->
        HomeUiState(
            items = partial.items,
            categories = partial.categories,
            folders = partial.folders,
            tasks = partial.tasks,
            stats = partial.stats,
            filter = filter.value,
            sort = sort.value,
            telegramConnected = partial.connected,
            isRefreshing = refreshing,
            message = status
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setFilter(value: ItemFilter) {
        filter.value = value
    }

    fun setSort(value: SortMode) {
        sort.value = value
    }

    fun refresh() = viewModelScope.launch {
        isRefreshing.value = true
        message.value = null
        try {
            repository.queueAllForSync()
            message.value = if (telegram.configured.value) {
                "Sync requested. Pending items will upload in the background."
            } else {
                "Telegram is not connected. Everything is safely stored on this device."
            }
        } catch (error: Exception) {
            message.value = error.message ?: "Sync could not be started. Your local data is unchanged."
        } finally {
            isRefreshing.value = false
        }
    }

    fun clearMessage() {
        message.value = null
    }

    fun toggleFavorite(id: String) = viewModelScope.launch { repository.toggleFavorite(id) }

    fun duplicate(id: String) = viewModelScope.launch { repository.duplicateItem(id) }

    fun delete(id: String, cloudDelete: Boolean) = viewModelScope.launch {
        repository.deleteItem(id, cloudDelete)
    }

    fun move(id: String, folderId: String?) = viewModelScope.launch {
        repository.moveItem(id, folderId)
    }

    fun setViewMode(value: com.promptvault.domain.ViewMode) = viewModelScope.launch {
        settingsStore.setViewMode(value)
    }
}
