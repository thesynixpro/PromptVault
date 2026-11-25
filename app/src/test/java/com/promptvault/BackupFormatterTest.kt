package com.promptvault

import com.promptvault.domain.BackupFormatter
import com.promptvault.domain.ContentType
import com.promptvault.domain.PromptItem
import com.promptvault.domain.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupFormatterTest {
    private fun item(content: String) = PromptItem(
        id = "item-1",
        title = "Bangla notes",
        content = content,
        contentType = ContentType.TEXT,
        tags = listOf("notes", "বাংলা"),
        categoryName = "Personal",
        folderName = "Archive",
        localVersion = 3,
        updatedAt = 123L,
        syncStatus = SyncStatus.PENDING
    )

    @Test
    fun shortTextUsesMessageMetadata() {
        val message = BackupFormatter.shortMessage(item("Hello\nworld"))
        assertTrue(message.contains("ID: item-1"))
        assertTrue(message.contains("Version: 3"))
        assertTrue(message.contains("--- CONTENT ---"))
        assertTrue(message.contains("Hello\nworld"))
    }

    @Test
    fun longTextUsesDocumentWithoutTruncation() {
        val content = "বাংলা টেক্সট\n".repeat(500)
        assertTrue(BackupFormatter.shouldUseDocument(content))
        val document = BackupFormatter.buildDocument(item(content))
        assertTrue(document.contains(content))
        assertTrue(document.contains("title=Bangla notes"))
    }

    @Test
    fun shortTextStaysWithinTelegramLimit() {
        val content = "abcd ".repeat(400)
        assertFalse(BackupFormatter.shouldUseDocument(content))
        assertTrue(BackupFormatter.shortMessage(item(content)).length < 4_100)
    }

    @Test
    fun previewFlattensLineBreaks() {
        assertEquals("One two three", BackupFormatter.preview("One\ntwo  three", 40))
    }
}
