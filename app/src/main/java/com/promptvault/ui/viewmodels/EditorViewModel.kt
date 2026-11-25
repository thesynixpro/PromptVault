package com.promptvault.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptvault.AppContainer
import com.promptvault.data.repository.ItemInput
import com.promptvault.domain.Category
import com.promptvault.domain.ContentType
import com.promptvault.domain.Folder
import com.promptvault.domain.PromptItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque

data class EditorUiState(
    val itemId: String,
    val draftKey: String,
    val isNew: Boolean,
    val loaded: Boolean = false,
    val title: String = "",
    val content: String = "",
    val contentType: ContentType = ContentType.PROMPT,
    val categoryId: String? = null,
    val folderId: String? = null,
    val tagsRaw: String = "",
    val isFavorite: Boolean = false,
    val dirty: Boolean = false,
    val draftSaved: Boolean = false,
    val savedItemId: String? = null,
    val error: String? = null,
    val categories: List<Category> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val fontSize: Int = 16,
    val lineSpacing: Int = 4,
    val autoSave: Boolean = true,
    val wordCount: Int = 0,
    val characterCount: Int = 0,
    val lineCount: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

class EditorViewModel(
    container: AppContainer,
    itemId: String,
    initialType: ContentType
) : ViewModel() {
    private val repository = container.repository
    private val settingsStore = container.settings
    private val isNew = itemId == "new" || itemId.startsWith("new_")
    private val draftKey = if (isNew) "new_${initialType.name}" else itemId
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var lastSavedSnapshot: String? = null

    private val _state = MutableStateFlow(
        EditorUiState(
            itemId = itemId,
            draftKey = draftKey,
            isNew = isNew,
            contentType = initialType
        )
    )
    val state: StateFlow<EditorUiState> = _state

    init {
        viewModelScope.launch {
            val prefs = settingsStore.settings.first()
            val draft = repository.getDraft(draftKey)
            val existing: PromptItem? = if (isNew) null else repository.getItem(itemId)
            val base: PromptItem? = draft?.let {
                PromptItem(
                    id = draft.itemId,
                    title = draft.title,
                    content = draft.content,
                    contentType = runCatching { ContentType.valueOf(draft.contentType) }
                        .getOrDefault(initialType),
                    categoryId = draft.categoryId,
                    folderId = draft.folderId,
                    tags = draft.tags,
                    isFavorite = draft.isFavorite
                )
            } ?: existing
            val type = base?.contentType ?: if (isNew) prefs.defaultContentType else initialType
            _state.update {
                it.copy(
                    loaded = true,
                    title = base?.title.orEmpty(),
                    content = base?.content.orEmpty(),
                    contentType = type,
                    categoryId = base?.categoryId,
                    folderId = base?.folderId,
                    tagsRaw = base?.tags?.joinToString(", ").orEmpty(),
                    isFavorite = base?.isFavorite ?: false,
                    dirty = draft != null,
                    savedItemId = if (!isNew) itemId else null,
                    fontSize = prefs.fontSize,
                    lineSpacing = prefs.lineSpacing,
                    autoSave = prefs.autoSave,
                    wordCount = wordCount(base?.content.orEmpty()),
                    characterCount = base?.content?.length ?: 0,
                    lineCount = lineCount(base?.content.orEmpty())
                )
            }
        }

        viewModelScope.launch {
            repository.observeCategories().collect { categories ->
                _state.update { it.copy(categories = categories) }
            }
        }
        viewModelScope.launch {
            repository.observeFolders().collect { folders ->
                _state.update { it.copy(folders = folders) }
            }
        }

        viewModelScope.launch {
            while (isActive) {
                delay(900)
                val current = _state.value
                if (!current.loaded || !current.dirty || !current.autoSave) continue
                val snapshot = snapshot(current)
                if (snapshot == lastSavedSnapshot) continue
                runCatching {
                    repository.saveDraft(current.asInput(), current.draftKey)
                    lastSavedSnapshot = snapshot
                    _state.update { it.copy(draftSaved = true) }
                }
            }
        }
    }

    fun onTitleChange(value: String) = updateState { copy(title = value, error = null, dirty = true) }

    fun onContentChange(value: String) {
        val previous = _state.value.content
        if (previous == value) return
        undoStack.addLast(previous)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        updateState {
            copy(
                content = value,
                error = null,
                dirty = true,
                wordCount = wordCount(value),
                characterCount = value.length,
                lineCount = lineCount(value),
                canUndo = true,
                canRedo = false
            )
        }
    }

    fun onTagsChange(value: String) = updateState { copy(tagsRaw = value, dirty = true) }

    fun onCategoryChange(id: String?) = updateState { copy(categoryId = id, dirty = true) }

    fun onFolderChange(id: String?) = updateState { copy(folderId = id, dirty = true) }

    fun onTypeChange(type: ContentType) = updateState { copy(contentType = type, dirty = true) }

    fun toggleFavorite() = updateState { copy(isFavorite = !isFavorite, dirty = true) }

    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeLast()
        redoStack.addLast(_state.value.content)
        updateState {
            copy(
                content = previous,
                dirty = true,
                wordCount = wordCount(previous),
                characterCount = previous.length,
                lineCount = lineCount(previous),
                canUndo = undoStack.isNotEmpty(),
                canRedo = true
            )
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(_state.value.content)
        updateState {
            copy(
                content = next,
                dirty = true,
                wordCount = wordCount(next),
                characterCount = next.length,
                lineCount = lineCount(next),
                canUndo = true,
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun save() = viewModelScope.launch {
        val current = _state.value
        if (current.title.isBlank()) {
            _state.update { it.copy(error = "Add a title before saving.") }
            return@launch
        }
        if (current.content.isBlank()) {
            _state.update { it.copy(error = "Content cannot be empty.") }
            return@launch
        }
        val input = current.asInput()
        val savedId = if (current.isNew) {
            repository.createItem(input)
        } else {
            repository.updateItem(current.itemId, input)
            current.itemId
        }
        repository.clearDraft(current.draftKey)
        lastSavedSnapshot = null
        _state.update {
            it.copy(
                dirty = false,
                draftSaved = false,
                savedItemId = savedId,
                error = null
            )
        }
    }

    fun delete() = viewModelScope.launch {
        val current = _state.value
        repository.clearDraft(current.draftKey)
        if (!current.isNew) repository.deleteItem(current.itemId, false)
    }

    fun duplicate() = viewModelScope.launch {
        val current = _state.value
        if (!current.isNew) repository.duplicateItem(current.itemId)
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun updateState(block: EditorUiState.() -> EditorUiState) {
        _state.update { block(it) }
    }

    private fun snapshot(state: EditorUiState): String = listOf(
        state.title,
        state.content,
        state.contentType.name,
        state.categoryId.orEmpty(),
        state.folderId.orEmpty(),
        state.tagsRaw,
        state.isFavorite.toString()
    ).joinToString("\u0001")

    private fun EditorUiState.asInput() = ItemInput(
        title = title,
        content = content,
        contentType = contentType,
        categoryId = categoryId,
        folderId = folderId,
        tags = tagsRaw.split(',').map { it.trim() }.filter { it.isNotBlank() },
        isFavorite = isFavorite
    )

    private fun wordCount(text: String) = text.split(Regex("\\s+")).count { it.isNotBlank() }

    private fun lineCount(text: String) = if (text.isEmpty()) 0 else text.lines().size

    private companion object {
        const val MAX_HISTORY = 80
    }
}
