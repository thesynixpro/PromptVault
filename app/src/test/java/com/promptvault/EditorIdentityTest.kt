package com.promptvault

import com.promptvault.domain.ContentType
import com.promptvault.domain.EditorIdentity
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EditorIdentityTest {
    @Test
    fun detectsAllNewEditorIds() {
        assertTrue(EditorIdentity.isNew("new"))
        assertTrue(EditorIdentity.isNew("new_${UUID.randomUUID()}"))
        assertFalse(EditorIdentity.isNew(UUID.randomUUID().toString()))
    }

    @Test
    fun legacyNewRouteUsesTypeSpecificDraftKey() {
        assertEquals("new_PROMPT", EditorIdentity.draftKey("new", ContentType.PROMPT))
        assertEquals("new_TEXT", EditorIdentity.draftKey("new", ContentType.TEXT))
        assertEquals("new_CODE", EditorIdentity.draftKey("new", ContentType.CODE))
    }

    @Test
    fun uniqueRouteUsesItsOwnDraftKey() {
        val id = EditorIdentity.newItemId()
        assertEquals(id, EditorIdentity.draftKey(id, ContentType.TEXT))
    }

    @Test
    fun generatedIdsAreUniqueAndParseable() {
        val ids = (1..100).map { EditorIdentity.newItemId() }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertTrue(id.startsWith("new_"))
            assertEquals(id, "new_${UUID.fromString(id.removePrefix("new_"))}")
        }
        assertNotEquals(ids.first(), ids.last())
    }
}
