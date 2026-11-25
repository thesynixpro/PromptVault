package com.promptvault.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptvault.AppContainer
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

data class FoldersUiState(
    val folders: List<Folder> = emptyList(),
    val childFolders: List<Folder> = emptyList(),
    val items: List<PromptItem> = emptyList(),
    val parentId: String? = null,
    val currentFolder: Folder? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class FoldersViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val parentId = MutableStateFlow<String?>(null)

    private val items = repository.observeItems(ItemFilter.ALL, SortMode.RECENTLY_MODIFIED)

    val uiState: StateFlow<FoldersUiState> = combine(
        repository.observeFolders(),
        items,
        parentId
    ) { folders, allItems, currentParent ->
        FoldersUiState(
            folders = folders,
            childFolders = folders.filter { it.parentFolderId == currentParent },
            items = allItems.filter {
                if (currentParent == null) it.folderId == null else it.folderId == currentParent
            },
            parentId = currentParent,
            currentFolder = folders.firstOrNull { it.id == currentParent }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldersUiState())

    fun openFolder(id: String?) {
        parentId.value = id
    }

    fun create(name: String, color: Long, icon: String) = viewModelScope.launch {
        repository.createFolder(name, parentId.value, color, icon)
    }

    fun update(id: String, name: String, color: Long, icon: String) = viewModelScope.launch {
        repository.updateFolder(id, name, color, icon)
    }

    fun delete(id: String, deleteContents: Boolean) = viewModelScope.launch {
        repository.deleteFolder(id, deleteContents)
    }

    fun moveItem(id: String, folderId: String?) = viewModelScope.launch {
        repository.moveItem(id, folderId)
    }

    fun toggleFavorite(id: String) = viewModelScope.launch {
        repository.toggleFavorite(id)
    }
}
