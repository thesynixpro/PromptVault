package com.promptvault

import com.promptvault.domain.BackupFiles
import com.promptvault.domain.BackupFormatter
import com.promptvault.domain.ContentType
import com.promptvault.domain.ItemFilter
import com.promptvault.domain.PromptItem
import com.promptvault.domain.SearchEngine
import com.promptvault.domain.SortMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomainEdgeCasesTest {
    private val items = listOf(
        PromptItem(
            id = "bangla",
            title = "প্রম্পট",
            content = "বাংলা উত্তর\nলাইন দুই",
            contentType = ContentType.PROMPT,
            tags = listOf("চ্যাট", "AI"),
            categoryName = "কাজ",
            folderName = "আর্কাইভ",
            createdAt = 10L,
            updatedAt = 30L
        ),
        PromptItem(
            id = "code",
            title = "Kotlin Flow",
            content = "flowOn(Dispatchers.IO)",
            contentType = ContentType.CODE,
            tags = listOf("kotlin"),
            createdAt = 20L,
            updatedAt = 20L
        )
    )

    @Test
    fun searchIgnoresSurroundingWhitespaceAndCase() {
        val results = SearchEngine.filter(items, ItemFilter.ALL, "  কাজ  ")
        assertEquals(listOf("bangla"), results.map { it.id })
        assertTrue(
            SearchEngine.filter(items, ItemFilter.ALL, "FLOW").any { it.id == "code" }
        )
        assertTrue(
            SearchEngine.filter(items, ItemFilter.ALL, "চ্যাট").any { it.id == "bangla" }
        )
    }

    @Test
    fun searchTreatsSqlWildcardsAsLiteralText() {
        val literal = PromptItem(
            id = "literal",
            title = "100% _done_",
            content = "literal match only",
            contentType = ContentType.TEXT
        )
        val corpus = items + literal
        assertEquals(
            listOf("literal"),
            SearchEngine.filter(corpus, ItemFilter.ALL, "100%").map { it.id }
        )
        assertEquals(
            listOf("literal"),
            SearchEngine.filter(corpus, ItemFilter.ALL, "_done_").map { it.id }
        )
    }

    @Test
    fun suggestionsAreLimitedAndFiltered() {
        val recent = listOf("alpha", "Bangla prompt", "beta", "alphabet", "gamma")
        assertEquals(listOf("alpha", "alphabet"), SearchEngine.suggestions(recent, "ALPHA"))
        assertEquals(4, SearchEngine.suggestions(listOf("a", "b", "c", "d"), "").size)
    }

    @Test
    fun sortingIsStableForEqualTimestamps() {
        val tied = listOf(
            PromptItem(id = "z", title = "Zulu", createdAt = 5L, updatedAt = 5L),
            PromptItem(id = "a", title = "Alpha", createdAt = 5L, updatedAt = 5L)
        )
        assertEquals(listOf("z", "a"), SearchEngine.sort(tied, SortMode.NEWEST).map { it.id })
        assertEquals(listOf("a", "z"), SearchEngine.sort(tied, SortMode.ALPHABETICAL).map { it.id })
    }

    @Test
    fun longDocumentUsesDocumentFormatWithoutTruncation() {
        val content = "short line\n".repeat(250)
        assertTrue(BackupFormatter.shouldUseDocument(content))
        val document = BackupFormatter.buildDocument(items.first().copy(content = content))
        assertTrue(document.contains(content))
        assertTrue(document.contains("title=প্রম্পট"))
        assertTrue(document.contains("tags=চ্যাট|AI"))
    }

    @Test
    fun shortMessageRemovesNullBytesAndKeepsUnicode() {
        val message = BackupFormatter.shortMessage(
            items.first().copy(content = "第一行\u0000 second\r\nthird")
        )
        assertFalse(message.contains("\u0000"))
        assertTrue(message.contains("第一行 second\nthird"))
        assertTrue(message.contains("ID: bangla"))
    }

    @Test
    fun backupRoundTripPreservesLargeUnicodePayload() {
        val content = "লাইন ১\n".repeat(1_000)
        val payload = BackupFiles.Payload(
            exportedAt = 99L,
            items = listOf(
                BackupFiles.BackupItem(
                    id = "large",
                    title = "বড় নথি",
                    content = content,
                    contentType = "TEXT",
                    tags = listOf("বাংলা", "backup")
                )
            )
        )
        val decoded = BackupFiles.decode(BackupFiles.encode(payload))
        assertEquals(1, decoded?.items?.size)
        assertEquals(content, decoded?.items?.first()?.content)
        assertEquals(99L, decoded?.exportedAt)
    }
}
