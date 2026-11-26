package com.promptvault.domain

import java.util.UUID

/** Pure helpers shared by the editor and navigation so draft isolation is testable. */
object EditorIdentity {
    fun isNew(itemId: String): Boolean =
        itemId == "new" || itemId.startsWith("new_")

    /**
     * Legacy "new" routes were shared by every content type, so they need a type-specific
     * draft key. Modern routes already carry a unique ID and can safely use it as the key.
     */
    fun draftKey(itemId: String, initialType: ContentType): String =
        if (itemId == "new") "new_${initialType.name}" else itemId

    fun newItemId(): String = "new_${UUID.randomUUID()}"
}
