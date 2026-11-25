package com.promptvault

import com.promptvault.domain.ContentType
import com.promptvault.domain.ItemFilter
import com.promptvault.domain.PromptItem
import com.promptvault.domain.SearchEngine
import com.promptvault.domain.SortMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchEngineTest {
    private val items = listOf(
        PromptItem(id = "1", title = "Bangla prompt", content = "বাংলা প্রম্পট", contentType = ContentType.PROMPT, tags = listOf("ai", "bangla"), isFavorite = true, createdAt = 100, updatedAt = 300),
        PromptItem(id = "2", title = "Kotlin coroutine", content = "viewModelScope.launch", contentType = ContentType.CODE, tags = listOf("kotlin"), createdAt = 200, updatedAt = 200),
        PromptItem(id = "3", title = "Meeting note", content = "Follow up tomorrow", contentType = ContentType.TEXT, categoryName = "Work", createdAt = 300, updatedAt = 100)
    )

    @Test
    fun filtersFavoritesAndCode() {
        assertEquals(1, SearchEngine.filter(items, ItemFilter.FAVORITES).size)
        assertEquals(1, SearchEngine.filter(items, ItemFilter.CODE).size)
        assertEquals(3, SearchEngine.filter(items, ItemFilter.ALL).size)
    }

    @Test
    fun searchesUnicodeContentAndTags() {
        assertTrue(SearchEngine.filter(items, ItemFilter.ALL, "বাংলা").any { it.id == "1" })
        assertTrue(SearchEngine.filter(items, ItemFilter.ALL, "kotlin").any { it.id == "2" })
        assertTrue(SearchEngine.filter(items, ItemFilter.ALL, "Work").any { it.id == "3" })
    }

    @Test
    fun sortsByModifiedAndAlphabetical() {
        assertEquals(listOf("1", "2", "3"), SearchEngine.sort(items, SortMode.RECENTLY_MODIFIED).map { it.id })
        assertEquals(listOf("1", "3", "2"), SearchEngine.sort(items, SortMode.ALPHABETICAL).map { it.id })
    }
}
