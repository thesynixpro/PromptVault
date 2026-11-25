package com.promptvault.domain

object SearchEngine {
    fun filter(
        items: List<PromptItem>,
        filter: ItemFilter,
        query: String = ""
    ): List<PromptItem> {
        val term = query.trim()
        var result = when (filter) {
            ItemFilter.ALL, ItemFilter.RECENT -> items
            ItemFilter.PROMPTS -> items.filter { it.contentType == ContentType.PROMPT }
            ItemFilter.TEXT -> items.filter { it.contentType == ContentType.TEXT }
            ItemFilter.CODE -> items.filter { it.contentType == ContentType.CODE }
            ItemFilter.FAVORITES -> items.filter { it.isFavorite }
            ItemFilter.FOLDERS -> items
        }
        if (term.isNotBlank()) {
            result = result.filter { item -> matches(item, term) }
        }
        return result
    }

    fun sort(items: List<PromptItem>, mode: SortMode): List<PromptItem> = when (mode) {
        SortMode.NEWEST -> items.sortedByDescending { it.createdAt }
        SortMode.OLDEST -> items.sortedBy { it.createdAt }
        SortMode.RECENTLY_MODIFIED -> items.sortedByDescending { it.updatedAt }
        SortMode.ALPHABETICAL -> items.sortedBy { it.title.lowercase() }
    }

    fun suggestions(recent: List<String>, query: String): List<String> {
        val term = query.trim()
        return if (term.isBlank()) recent.take(8)
        else recent.filter { it.contains(term, ignoreCase = true) }.take(8)
    }

    private fun matches(item: PromptItem, term: String): Boolean {
        val needle = term.lowercase()
        return listOf(
            item.title,
            item.content,
            item.categoryName,
            item.folderName,
            item.tags.joinToString(" ")
        ).any { it.lowercase().contains(needle) }
    }
}
