package com.astrawave.app.data

import android.content.Context
import android.util.Base64
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
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import org.xmlpull.v1.XmlPullParser

/**
 * WebDAV-compatible adapter for user-owned WebDAV/NAS endpoints.
 * NAS support here intentionally means a NAS exposing an HTTP(S) WebDAV endpoint; SMB credentials
 * and raw SMB browsing are not implemented by this adapter.
 */
class WebDavPersonalMediaGateway(context: Context) : PersonalMediaGateway {
    private val credentials = PersonalMediaCredentialStore(context)

    override fun test(connection: PersonalMediaConnection): PersonalMediaConnection {
        if (connection.provider != PersonalMediaProvider.WEBDAV && connection.provider != PersonalMediaProvider.NAS) {
            return connection.copy(
                status = PersonalMediaConnectionStatus.ERROR,
                lastError = "This adapter supports WebDAV-compatible endpoints only",
            )
        }

        return runCatching {
            val entries = listAt(connection, connection.serverUrl)
            connection.copy(
                status = PersonalMediaConnectionStatus.READY,
                libraryCount = 1,
                itemCount = entries.count { !it.collection },
                lastSyncEpochMs = System.currentTimeMillis(),
                lastError = null,
            )
        }.getOrElse { error ->
            connection.copy(
                status = PersonalMediaConnectionStatus.ERROR,
                lastSyncEpochMs = System.currentTimeMillis(),
                lastError = error.message ?: "WebDAV connection test failed",
            )
        }
    }

    override fun libraries(connection: PersonalMediaConnection): List<PersonalMediaLibrary> {
        val entries = listAt(connection, connection.serverUrl)
        return listOf(
            PersonalMediaLibrary(
                id = "root",
                connectionId = connection.id,
                name = connection.name,
                mediaTypes = entries.mapNotNull { classify(it.name) }.toSet().ifEmpty {
                    setOf(LibraryMediaType.MOVIE, LibraryMediaType.SERIES, LibraryMediaType.MUSIC)
                },
                itemCount = entries.count { !it.collection },
            ),
        )
    }

    override fun recent(connection: PersonalMediaConnection, limit: Int): List<PersonalMediaItem> =
        listAt(connection, connection.serverUrl)
            .filterNot { it.collection }
            .mapNotNull { it.toMediaItem(connection) }
            .take(limit.coerceIn(1, 100))

    /**
     * Searches nested folders with bounded breadth-first traversal. This makes normal personal
     * libraries such as /Movies, /TV/Show/Season and /Music visible without allowing one search
     * to crawl an unbounded NAS tree.
     */
    override fun search(connection: PersonalMediaConnection, query: String, limit: Int): List<PersonalMediaItem> {
        val needle = query.trim()
        if (needle.isBlank()) return emptyList()
        val capped = limit.coerceIn(1, 100)
        val queue = ArrayDeque<SearchDirectory>()
        val visited = mutableSetOf<String>()
        val results = mutableListOf<PersonalMediaItem>()
        queue.add(SearchDirectory(connection.serverUrl.ensureTrailingSlash(), depth = 0))

        while (queue.isNotEmpty() && visited.size < MAX_SEARCH_DIRECTORIES && results.size < capped) {
            val current = queue.removeFirst()
            val normalizedDirectory = normalizeUrl(current.url)
            if (!visited.add(normalizedDirectory)) continue

            val entries = runCatching { listAt(connection, current.url) }.getOrDefault(emptyList())
            entries.forEach { entry ->
                if (results.size >= capped) return@forEach
                if (entry.collection) {
                    if (current.depth < MAX_SEARCH_DEPTH && normalizeUrl(entry.href) !in visited) {
                        queue.add(SearchDirectory(entry.href.ensureTrailingSlash(), current.depth + 1))
                    }
                } else if (entry.name.contains(needle, ignoreCase = true)) {
                    entry.toMediaItem(connection)?.let(results::add)
                }
            }
        }

        return results.distinctBy { it.id }.take(capped)
    }

    private fun listAt(connection: PersonalMediaConnection, directoryUrl: String): List<WebDavEntry> {
        val request = URL(directoryUrl).openConnection() as HttpURLConnection
        request.instanceFollowRedirects = true
        request.connectTimeout = 10_000
        request.readTimeout = 15_000
        request.requestMethod = "PROPFIND"
        request.setRequestProperty("Depth", "1")
        request.setRequestProperty("Accept", "application/xml, text/xml")
        request.setRequestProperty("User-Agent", "AstraWave/0.1")
        applyBasicAuth(request, connection.id)
        request.doOutput = true
        request.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
        request.outputStream.use { output ->
            output.write(
                """<?xml version=\"1.0\" encoding=\"utf-8\" ?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:displayname/><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>"""
                    .toByteArray(StandardCharsets.UTF_8),
            )
        }

        val code = request.responseCode
        val stream = if (code in 200..299) request.inputStream else request.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("WebDAV endpoint returned HTTP $code")
        return parseMultiStatus(body, directoryUrl)
            .filterNot { normalizeUrl(it.href) == normalizeUrl(directoryUrl) }
    }

    private fun applyBasicAuth(connection: HttpURLConnection, connectionId: String) {
        val username = credentials.loadUsername(connectionId)
        val password = credentials.loadPassword(connectionId)
        if (username.isNullOrBlank() && password.isNullOrBlank()) return
        require(!username.isNullOrBlank() && password != null) {
            "Both username and password are required for WebDAV authentication"
        }
        val value = Base64.encodeToString(
            "$username:$password".toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP,
        )
        connection.setRequestProperty("Authorization", "Basic $value")
    }

    private fun parseMultiStatus(xml: String, serverUrl: String): List<WebDavEntry> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }
        val result = mutableListOf<WebDavEntry>()
        var event = parser.eventType
        var href: String? = null
        var displayName: String? = null
        var collection = false
        var insideResponse = false

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':').lowercase()) {
                    "response" -> {
                        insideResponse = true
                        href = null
                        displayName = null
                        collection = false
                    }
                    "href" -> if (insideResponse) href = parser.nextText()
                    "displayname" -> if (insideResponse) displayName = parser.nextText()
                    "collection" -> if (insideResponse) collection = true
                }
                XmlPullParser.END_TAG -> if (
                    parser.name.substringAfter(':').equals("response", ignoreCase = true) && insideResponse
                ) {
                    val rawHref = href.orEmpty()
                    if (rawHref.isNotBlank()) {
                        val resolved = resolveHref(serverUrl, rawHref)
                        val fallback = resolved.trimEnd('/').substringAfterLast('/').ifBlank { "Media" }
                        result += WebDavEntry(
                            href = resolved,
                            name = displayName?.takeIf { it.isNotBlank() } ?: decode(fallback),
                            collection = collection,
                        )
                    }
                    insideResponse = false
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun resolveHref(serverUrl: String, href: String): String = runCatching {
        val base = URI(serverUrl.ensureTrailingSlash())
        base.resolve(href).toString()
    }.getOrElse { href }

    private fun WebDavEntry.toMediaItem(connection: PersonalMediaConnection): PersonalMediaItem? {
        val type = classify(name) ?: return null
        return PersonalMediaItem(
            id = href,
            connectionId = connection.id,
            libraryId = "root",
            type = type,
            title = name.substringBeforeLast('.', name),
            streamUrl = href,
        )
    }

    private fun classify(name: String): LibraryMediaType? = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v", "mkv", "avi", "mov", "webm", "ts", "m2ts" -> LibraryMediaType.MOVIE
        "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav" -> LibraryMediaType.MUSIC
        else -> null
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)

    private fun normalizeUrl(value: String): String = value.trimEnd('/')
    private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"

    private data class SearchDirectory(val url: String, val depth: Int)

    private data class WebDavEntry(
        val href: String,
        val name: String,
        val collection: Boolean,
    )

    private companion object {
        const val MAX_SEARCH_DEPTH = 5
        const val MAX_SEARCH_DIRECTORIES = 60
    }
}
