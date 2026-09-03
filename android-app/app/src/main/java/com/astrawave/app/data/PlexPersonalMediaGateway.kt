package com.astrawave.app.data

import android.content.Context
import android.util.Xml
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.core.PersonalMediaConnection
import com.astrawave.app.core.PersonalMediaConnectionStatus
import com.astrawave.app.core.PersonalMediaGateway
import com.astrawave.app.core.PersonalMediaItem
import com.astrawave.app.core.PersonalMediaLibrary
import com.astrawave.app.core.PersonalMediaProvider
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.xmlpull.v1.XmlPullParser

/**
 * Authenticated adapter for a user-owned Plex Media Server.
 * The Plex token is read from Android-Keystore-backed PersonalMediaCredentialStore and is never
 * written into PersonalMediaConnection metadata or returned media URLs.
 */
class PlexPersonalMediaGateway(context: Context) : PersonalMediaGateway {
    private val credentials = PersonalMediaCredentialStore(context)

    override fun test(connection: PersonalMediaConnection): PersonalMediaConnection {
        if (connection.provider != PersonalMediaProvider.PLEX) {
            return connection.copy(
                status = PersonalMediaConnectionStatus.ERROR,
                lastError = "This adapter supports Plex only",
            )
        }
        val token = credentials.loadToken(connection.id)
            ?: return connection.copy(
                status = PersonalMediaConnectionStatus.NOT_CONNECTED,
                lastError = "Secure Plex access token is required",
            )

        return runCatching {
            requestXml(connection.serverUrl, "/", token)
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
                lastError = error.message ?: "Plex server test failed",
            )
        }
    }

    override fun libraries(connection: PersonalMediaConnection): List<PersonalMediaLibrary> {
        val token = requireToken(connection)
        val xml = requestXml(connection.serverUrl, "/library/sections", token)
        return parseElements(xml, setOf("Directory")).mapNotNull { attrs ->
            val key = attrs["key"].orEmpty()
            val title = attrs["title"].orEmpty()
            if (key.isBlank() || title.isBlank()) return@mapNotNull null
            val type = attrs["type"].orEmpty().lowercase()
            PersonalMediaLibrary(
                id = key,
                connectionId = connection.id,
                name = title,
                mediaTypes = when (type) {
                    "movie" -> setOf(LibraryMediaType.MOVIE)
                    "show" -> setOf(LibraryMediaType.SERIES, LibraryMediaType.EPISODE)
                    "artist" -> setOf(LibraryMediaType.MUSIC)
                    else -> setOf(LibraryMediaType.MOVIE, LibraryMediaType.SERIES, LibraryMediaType.EPISODE)
                },
            )
        }
    }

    override fun recent(connection: PersonalMediaConnection, limit: Int): List<PersonalMediaItem> {
        val token = requireToken(connection)
        val path = "/library/recentlyAdded?X-Plex-Container-Start=0&X-Plex-Container-Size=${limit.coerceIn(1, 100)}"
        return parseMediaItems(connection, requestXml(connection.serverUrl, path, token)).take(limit.coerceIn(1, 100))
    }

    override fun search(connection: PersonalMediaConnection, query: String, limit: Int): List<PersonalMediaItem> {
        if (query.isBlank()) return emptyList()
        val token = requireToken(connection)
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val path = "/hubs/search?query=$encoded&limit=${limit.coerceIn(1, 100)}"
        return parseMediaItems(connection, requestXml(connection.serverUrl, path, token)).take(limit.coerceIn(1, 100))
    }

    private fun parseMediaItems(connection: PersonalMediaConnection, xml: String): List<PersonalMediaItem> =
        parseElements(xml, setOf("Video", "Directory", "Track")).mapNotNull { attrs ->
            val id = attrs["ratingKey"].orEmpty().ifBlank { attrs["key"].orEmpty() }
            val title = attrs["title"].orEmpty()
            if (id.isBlank() || title.isBlank()) return@mapNotNull null

            val type = when (attrs["type"].orEmpty().lowercase()) {
                "movie" -> LibraryMediaType.MOVIE
                "show" -> LibraryMediaType.SERIES
                "episode" -> LibraryMediaType.EPISODE
                "track", "artist", "album" -> LibraryMediaType.MUSIC
                else -> return@mapNotNull null
            }
            val durationMs = attrs["duration"]?.toLongOrNull()?.takeIf { it > 0 }
            val libraryId = attrs["librarySectionID"].orEmpty().ifBlank {
                attrs["parentRatingKey"].orEmpty().ifBlank { "all" }
            }

            PersonalMediaItem(
                id = id,
                connectionId = connection.id,
                libraryId = libraryId,
                type = type,
                title = title,
                subtitle = attrs["grandparentTitle"]?.takeIf { it.isNotBlank() }
                    ?: attrs["parentTitle"]?.takeIf { it.isNotBlank() }
                    ?: attrs["year"]?.takeIf { it.isNotBlank() },
                durationMs = durationMs,
                streamUrl = null,
                externalId = attrs["guid"]?.takeIf { it.isNotBlank() },
            )
        }

    private fun parseElements(xml: String, names: Set<String>): List<Map<String, String>> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }
        val output = mutableListOf<Map<String, String>>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name in names) {
                val attrs = buildMap {
                    for (index in 0 until parser.attributeCount) {
                        put(parser.getAttributeName(index), parser.getAttributeValue(index).orEmpty())
                    }
                }
                output += attrs
            }
            event = parser.next()
        }
        return output
    }

    private fun requireToken(connection: PersonalMediaConnection): String =
        credentials.loadToken(connection.id) ?: error("Secure Plex access token is required")

    private fun requestXml(serverUrl: String, path: String, token: String): String {
        val separator = if ('?' in path) '&' else '?'
        val url = serverUrl.trimEnd('/') + path + separator + "X-Plex-Token=" +
            URLEncoder.encode(token, StandardCharsets.UTF_8.name())
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/xml")
        connection.setRequestProperty("X-Plex-Product", "AstraWave")
        connection.setRequestProperty("X-Plex-Version", "0.1")
        connection.setRequestProperty("User-Agent", "AstraWave/0.1")
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("Plex server returned HTTP $code")
        return body
    }
}
