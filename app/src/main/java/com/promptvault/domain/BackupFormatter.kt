package com.promptvault.domain

object BackupFormatter {
    const val TELEGRAM_MESSAGE_LIMIT = 3500

    fun shouldUseDocument(content: String): Boolean =
        content.toByteArray(Charsets.UTF_8).size > TELEGRAM_MESSAGE_LIMIT ||
            content.lines().size > 200

    fun shortMessage(item: PromptItem): String = buildString {
        append("PromptVault backup item\n")
        append("ID: ${item.id}\n")
        append("Title: ${item.title}\n")
        append("Type: ${item.contentType.name}\n")
        if (item.categoryName.isNotBlank()) append("Category: ${item.categoryName}\n")
        if (item.folderName.isNotBlank()) append("Folder: ${item.folderName}\n")
        if (item.tags.isNotEmpty()) append("Tags: ${item.tags.joinToString(", ")}\n")
        append("Version: ${item.localVersion}\n")
        append("Updated: ${item.updatedAt}\n")
        append("\n--- CONTENT ---\n")
        append(safeMessageText(item.content))
    }

    fun buildDocument(item: PromptItem): String = buildString {
        append("PromptVault UTF-8 backup\n")
        append("id=${item.id}\n")
        append("title=${singleLine(item.title)}\n")
        append("type=${item.contentType.name}\n")
        append("category=${singleLine(item.categoryName)}\n")
        append("folder=${singleLine(item.folderName)}\n")
        append("tags=${item.tags.joinToString("|") { singleLine(it) }}\n")
        append("version=${item.localVersion}\n")
        append("updated_at=${item.updatedAt}\n")
        append("content_type=${item.contentType.name}\n")
        append("--- CONTENT START ---\n")
        append(item.content)
        append("\n--- CONTENT END ---\n")
    }

    fun caption(item: PromptItem): String =
        "PromptVault • ${item.title.take(60)} • v${item.localVersion}"

    fun safeMessageText(text: String): String = text
        .replace("\u0000", "")
        .replace("\r\n", "\n")

    fun preview(text: String, max: Int = 140): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= max) flat else flat.take(max) + "…"
    }

    private fun singleLine(value: String): String =
        value.replace("\n", " ").replace("\r", " ")
}
