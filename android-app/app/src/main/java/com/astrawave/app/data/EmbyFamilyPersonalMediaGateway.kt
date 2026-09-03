package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.core.PersonalMediaConnection
import com.astrawave.app.core.PersonalMediaConnectionStatus
import com.astrawave.app.core.PersonalMediaGateway
import com.astrawave.app.core.PersonalMediaItem
import com.astrawave.app.core.PersonalMediaLibrary
import com.astrawave.app.core.PersonalMediaProvider
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Authenticated adapter for user-owned Jellyfin and Emby servers.
 * Tokens are read from Android-Keystore-backed PersonalMediaCredentialStore and are never
 * embedded into persisted connection metadata or returned stream URLs.
 */
class EmbyFamilyPersonalMediaGateway(context: Context) : PersonalMediaGateway {
    private val credentials = PersonalMediaCredentialStore(context)

    override fun test(connection: PersonalMediaConnection): PersonalMediaConnection {
        if (connection.provider != PersonalMediaProvider.JELLYFIN && connection.provider != PersonalMediaProvider.EMBY) {
            return connection.copy(
                status = PersonalMediaConnectionStatus.ERROR,
                lastError = "This adapter supports Jellyfin and Emby only",
            )
        }
        val token = credentials.loadToken(connection.id)
            ?: return connection.copy(
                status = PersonalMediaConnectionStatus.NOT_CONNECTED,
                lastError = "Secure access token is required",
            )

        return runCatching {
            requestJson(connection.serverUrl, "/System/Info", token)
            val libraries = libraries(connection)
            connection.copy(
                status = PersonalMediaConnectionStatus.READY,
                libraryCount = libraries.size,
                lastSyncEpochMs = System.currentTimeMillis(),
                lastError = null,
            )
        }.getOrElse { error ->
            connection.copy(
                status = PersonalMediaConnectionStatus.ERROR,
                lastSyncEpochMs = System.currentTimeMillis(),
                lastError = error.message ?: "Server test failed",
            )
        }
    }

    override fun libraries(connection: PersonalMediaConnection): List<PersonalMediaLibrary> {
        val token = requireToken(connection)
        val root = requestJson(connection.serverUrl, "/Library/MediaFolders", token)
        return itemsArray(root).mapNotNull { obj ->
            val id = obj.optString("Id")
            val name = obj.optString("Name")
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            PersonalMediaLibrary(
                id = id,
                connectionId = connection.id,
                name = name,
                mediaTypes = inferLibraryTypes(obj),
            )
        }
    }

    override fun recent(connection: PersonalMediaConnection, limit: Int): List<PersonalMediaItem> =
        queryItems(
            connection = connection,
            params = mapOf(
                "Recursive" to "true",
                "SortBy" to "DateCreated",
                "SortOrder" to "Descending",
                "IncludeItemTypes" to "Movie,Series,Episode",
                "Limit" to limit.coerceIn(1, 100).toString(),
            ),
        )

    override fun search(connection: PersonalMediaConnection, query: String, limit: Int): List<PersonalMediaItem> {
        if (query.isBlank()) return emptyList()
        return queryItems(
            connection = connection,
            params = mapOf(
                "Recursive" to "true",
                "SearchTerm" to query.trim(),
                "IncludeItemTypes" to "Movie,Series,Episode",
                "Limit" to limit.coerceIn(1, 100).toString(),
            ),
        )
    }

    private fun queryItems(
        connection: PersonalMediaConnection,
        params: Map<String, String>,
    ): List<PersonalMediaItem> {
        val token = requireToken(connection)
        val query = params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val root = requestJson(connection.serverUrl, "/Items?$query", token)
        return itemsArray(root).mapNotNull { obj -> toItem(connection, obj) }
    }

    private fun toItem(connection: PersonalMediaConnection, obj: JSONObject): PersonalMediaItem? {
        val id = obj.optString("Id")
        val title = obj.optString("Name")
        if (id.isBlank() || title.isBlank()) return null
        val type = when (obj.optString("Type").lowercase()) {
            "movie" -> LibraryMediaType.MOVIE
            "series" -> LibraryMediaType.SERIES
            "episode" -> LibraryMediaType.EPISODE
            else -> return null
        }
        val parentId = obj.optString("ParentId").ifBlank { obj.optString("SeriesId") }.ifBlank { "all" }
        val ticks = obj.optLong("RunTimeTicks", 0L)
        return PersonalMediaItem(
            id = id,
            connectionId = connection.id,
            libraryId = parentId,
            type = type,
            title = title,
            subtitle = obj.optString("SeriesName").takeIf { it.isNotBlank() },
            durationMs = ticks.takeIf { it > 0 }?.div(10_000L),
            streamUrl = null,
            externalId = obj.optJSONObject("ProviderIds")?.optString("Imdb")?.takeIf { it.isNotBlank() },
        )
    }

    private fun inferLibraryTypes(obj: JSONObject): Set<LibraryMediaType> {
        val collectionType = obj.optString("CollectionType").lowercase()
        return when (collectionType) {
            "movies" -> setOf(LibraryMediaType.MOVIE)
            "tvshows" -> setOf(LibraryMediaType.SERIES, LibraryMediaType.EPISODE)
            "music" -> setOf(LibraryMediaType.MUSIC)
            else -> setOf(LibraryMediaType.MOVIE, LibraryMediaType.SERIES, LibraryMediaType.EPISODE)
        }
    }

    private fun itemsArray(root: JSONObject): List<JSONObject> {
        val array = root.optJSONArray("Items") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add)
        }
    }

    private fun requireToken(connection: PersonalMediaConnection): String =
        credentials.loadToken(connection.id) ?: error("Secure access token is required")

    private fun requestJson(serverUrl: String, path: String, token: String): JSONObject {
        val connection = URL(serverUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-Emby-Token", token)
        connection.setRequestProperty("User-Agent", "AstraWave/0.1")
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("Server returned HTTP $code")
        return JSONObject(body)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
