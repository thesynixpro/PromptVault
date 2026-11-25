package com.promptvault

import com.promptvault.domain.BackupFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupFilesTest {
    @Test
    fun encodesAndDecodesRoundTripWithUnicode() {
        val payload = BackupFiles.Payload(
            exportedAt = 42L,
            items = listOf(
                BackupFiles.BackupItem(
                    id = "id-1",
                    title = "প্রম্পট",
                    content = "লাইন ১\nলাইন ২",
                    contentType = "PROMPT",
                    tags = listOf("বাংলা", "test")
                )
            )
        )
        val encoded = BackupFiles.encode(payload)
        val decoded = BackupFiles.decode(encoded)
        assertEquals(1, decoded?.items?.size)
        assertEquals("লাইন ১\nলাইন ২", decoded?.items?.first()?.content)
        assertEquals(listOf("বাংলা", "test"), decoded?.items?.first()?.tags)
    }

    @Test
    fun rejectsForeignJson() {
        assertNull(BackupFiles.decode("{\"hello\":\"world\"}"))
        assertNull(BackupFiles.decode("not json"))
    }

    @Test
    fun payloadUsesExpectedFormat() {
        val decoded = BackupFiles.decode(BackupFiles.encode(BackupFiles.Payload()))
        assertTrue(decoded?.format == "promptvault-backup")
        assertEquals(1, decoded?.version)
    }
}
