package com.promptvault.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

object BackupFiles {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class Payload(
        val format: String = "promptvault-backup",
        val version: Int = 1,
        val exportedAt: Long = 0L,
        val items: List<BackupItem> = emptyList(),
        val categories: List<BackupCategory> = emptyList(),
        val folders: List<BackupFolder> = emptyList()
    )

    @Serializable
    data class BackupItem(
        val id: String,
        val title: String,
        val content: String,
        val contentType: String,
        val categoryId: String? = null,
        val folderId: String? = null,
        val categoryName: String = "",
        val folderName: String = "",
        val tags: List<String> = emptyList(),
        val isFavorite: Boolean = false,
        val createdAt: Long = 0L,
        val updatedAt: Long = 0L,
        val localVersion: Int = 1
    )

    @Serializable
    data class BackupCategory(
        val id: String,
        val name: String,
        val color: Long,
        val icon: String
    )

    @Serializable
    data class BackupFolder(
        val id: String,
        val name: String,
        val parentFolderId: String?,
        val color: Long,
        val icon: String
    )

    fun encode(payload: Payload): String = json.encodeToString(payload)

    fun decode(text: String): Payload? = runCatching {
        val element = json.parseToJsonElement(text)
        val format = (element as? JsonObject)?.get("format") as? JsonPrimitive ?: return null
        if (format.content != "promptvault-backup") return null
        json.decodeFromJsonElement<Payload>(element)
    }.getOrNull()
}
