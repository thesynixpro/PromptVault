package com.promptvault.ui.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptvault.AppContainer
import com.promptvault.data.repository.ImportSummary
import com.promptvault.domain.ExportRecord
import com.promptvault.domain.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DownloadsUiState(
    val exports: List<ExportRecord> = emptyList(),
    val message: String = ""
)

class DownloadsViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository

    private val message = MutableStateFlow("")

    val uiState: StateFlow<DownloadsUiState> = combine(
        repository.observeExports(),
        message
    ) { exports, text -> DownloadsUiState(exports, text) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun clearMessage() {
        message.value = ""
    }

    fun exportAll(context: Context, uri: Uri) = viewModelScope.launch {
        persistPermission(context, uri)
        writeExport(
            context = context,
            uri = uri,
            mimeType = "application/json",
            contentProvider = { repository.createJsonBackup() }
        )
    }

    fun exportItem(context: Context, uri: Uri, itemId: String, asText: Boolean) = viewModelScope.launch {
        persistPermission(context, uri)
        if (asText) {
            writeExport(
                context = context,
                uri = uri,
                mimeType = "text/plain",
                contentProvider = { repository.createTextBackup(itemId) ?: "" }
            )
        } else {
            writeExport(
                context = context,
                uri = uri,
                mimeType = "application/json",
                contentProvider = { repository.createJsonBackup(setOf(itemId)) }
            )
        }
    }

    fun import(context: Context, uri: Uri) = viewModelScope.launch {
        persistPermission(context, uri)
        val name = displayName(context, uri).orEmpty()
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                    String(stream.readBytes(), Charsets.UTF_8)
                } ?: return@runCatching AppResult.Failure("Could not read the selected file.")

                if (name.endsWith(".json", ignoreCase = true) || text.trimStart().startsWith("{")) {
                    val summary = repository.importJson(text)
                    if (summary.error != null) {
                        AppResult.Failure(summary.error)
                    } else {
                        AppResult.Success("Imported ${summary.imported} items, skipped ${summary.skipped} duplicates.")
                    }
                } else {
                    val title = name.substringBeforeLast('.', name).ifBlank { "Imported text" }
                    repository.importText(text, title)
                    AppResult.Success("Imported text document “$title”.")
                }
            }.getOrElse { AppResult.Failure("Invalid or unsupported file.") }
        }
        message.value = when (result) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> result.message
        }
    }

    fun delete(record: ExportRecord) = viewModelScope.launch {
        repository.deleteExport(record)
    }

    private suspend fun writeExport(
        context: Context,
        uri: Uri,
        mimeType: String,
        contentProvider: suspend () -> String
    ) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val content = contentProvider()
                if (content.isBlank()) return@runCatching AppResult.Failure("There is nothing to export.")
                val bytes = content.toByteArray(Charsets.UTF_8)
                val output = context.contentResolver.openOutputStream(uri)
                    ?: return@runCatching AppResult.Failure("Could not write to the selected location.")
                output.use { it.write(bytes) }
                repository.recordExport(
                    name = displayName(context, uri) ?: "promptvault-export",
                    uri = uri.toString(),
                    mimeType = mimeType,
                    sizeBytes = bytes.size.toLong()
                )
                AppResult.Success("Exported ${formatSize(bytes.size.toLong())} to ${displayName(context, uri) ?: "file"}.")
            }.getOrElse { AppResult.Failure("Export failed. Check storage permissions and try again.") }
        }
        message.value = when (result) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> result.message
        }
    }

    private fun persistPermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    private fun formatSize(size: Long): String = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> "${size / (1024 * 1024)} MB"
    }
}
