package com.promptvault.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptvault.AppContainer
import com.promptvault.data.repository.ItemInput
import com.promptvault.domain.Category
import com.promptvault.domain.ContentType
import com.promptvault.domain.EditorIdentity
import com.promptvault.domain.Folder
import com.promptvault.domain.PromptItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val isSaving: Boolean = false,
    val deleteError: String? = null,
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
    private val isNew = EditorIdentity.isNew(itemId)
    private val draftKey = EditorIdentity.draftKey(itemId, initialType)
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private val operationMutex = Mutex()
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
                    draftSaved = draft != null,
                    fontSize = prefs.fontSize,
                    lineSpacing = prefs.lineSpacing,
                    autoSave = prefs.autoSave,
                    wordCount = wordCount(base?.content.orEmpty()),
                    characterCount = base?.content?.length ?: 0,
                    lineCount = lineCount(base?.content.orEmpty())
                )
            }
            lastSavedSnapshot = if (draft != null) snapshot(_state.value) else null
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
                if (!current.loaded || !current.dirty || !current.autoSave || current.isSaving) continue
                val snapshot = snapshot(current)
                if (snapshot == lastSavedSnapshot) continue
                operationMutex.withLock {
                    val latest = _state.value
                    if (!latest.dirty || !latest.autoSave || latest.isSaving) return@withLock
                    val latestSnapshot = snapshot(latest)
                    if (latestSnapshot == lastSavedSnapshot) return@withLock
                    runCatching {
                        repository.saveDraft(latest.asInput(), latest.draftKey)
                    }.onSuccess {
                        lastSavedSnapshot = latestSnapshot
                        _state.update { it.copy(draftSaved = true) }
                    }.onFailure { error ->
                        _state.update {
                            it.copy(error = error.message ?: "Draft could not be saved automatically.")
                        }
                    }
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
        if (_state.value.isSaving) return@launch
        _state.update { it.copy(isSaving = true, error = null) }
        try {
            operationMutex.withLock {
                val current = _state.value
                if (current.title.isBlank()) {
                    _state.update { it.copy(error = "Add a title before saving.") }
                    return@withLock
                }
                if (current.content.isBlank()) {
                    _state.update { it.copy(error = "Content cannot be empty.") }
                    return@withLock
                }
                val input = current.asInput()
                val savedId = if (current.isNew) {
                    repository.createItem(input)
                } else {
                    repository.updateItem(current.itemId, input)
                    current.itemId
                }
                val draftFailure = runCatching { repository.clearDraft(current.draftKey) }
                    .exceptionOrNull()
                lastSavedSnapshot = null
                _state.update {
                    it.copy(
                        // Keep the draft dirty so autosave rewrites any stale draft left behind.
                        dirty = draftFailure != null,
                        draftSaved = false,
                        savedItemId = savedId,
                        error = draftFailure?.let { error ->
                            "Saved, but the temporary draft could not be cleared: ${error.message}"
                        }
                    )
                }
            }
        } catch (error: Exception) {
            _state.update {
                it.copy(error = error.message ?: "The item could not be saved. Your text is still here.")
            }
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }

    /** Returns true only after the local database deletion has completed. */
    suspend fun delete(): Boolean {
        if (_state.value.isSaving) return false
        _state.update { it.copy(isSaving = true, deleteError = null) }
        return try {
            operationMutex.withLock {
                val current = _state.value
                if (!current.isNew) repository.deleteItem(current.itemId, false)
                val draftFailure = runCatching { repository.clearDraft(current.draftKey) }
                    .exceptionOrNull()
                if (draftFailure != null) {
                    _state.update {
                        it.copy(
                            deleteError = "Deleted, but the temporary draft could not be cleared: " +
                                draftFailure.message
                        )
                    }
                }
                true
            }
        } catch (error: Exception) {
            _state.update {
                it.copy(deleteError = error.message ?: "The item could not be deleted.")
            }
            false
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun duplicate() = viewModelScope.launch {
        try {
            operationMutex.withLock {
                val current = _state.value
                if (!current.isNew) repository.duplicateItem(current.itemId)
            }
        } catch (error: Exception) {
            _state.update { it.copy(error = error.message ?: "The item could not be duplicated.") }
        }
    }

    fun clearError() = _state.update { it.copy(error = null, deleteError = null) }

    fun consumeSaved() = _state.update { it.copy(savedItemId = null) }

    private fun updateState(block: EditorUiState.() -> EditorUiState) {
        _state.update { current ->
            val next = block(current)
            if (next != current && next.dirty) next.copy(draftSaved = false) else next
        }
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
