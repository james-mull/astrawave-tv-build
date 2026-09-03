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
            val entries = list(connection)
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
        val entries = list(connection)
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
        list(connection)
            .filterNot { it.collection }
            .mapNotNull { it.toMediaItem(connection) }
            .take(limit.coerceIn(1, 100))

    override fun search(connection: PersonalMediaConnection, query: String, limit: Int): List<PersonalMediaItem> {
        if (query.isBlank()) return emptyList()
        return list(connection)
            .asSequence()
            .filterNot { it.collection }
            .filter { it.name.contains(query.trim(), ignoreCase = true) }
            .mapNotNull { it.toMediaItem(connection) }
            .take(limit.coerceIn(1, 100))
            .toList()
    }

    private fun list(connection: PersonalMediaConnection): List<WebDavEntry> {
        val request = URL(connection.serverUrl).openConnection() as HttpURLConnection
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
        return parseMultiStatus(body, connection.serverUrl)
            .filterNot { normalizeUrl(it.href) == normalizeUrl(connection.serverUrl) }
    }

    private fun applyBasicAuth(connection: HttpURLConnection, connectionId: String) {
        val username = credentials.loadUsername(connectionId)
        val password = credentials.loadPassword(connectionId)
        if (username.isNullOrBlank() && password.isNullOrBlank()) return
        require(!username.isNullOrBlank() && password != null) { "Both username and password are required for WebDAV authentication" }
        val value = Base64.encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
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
                XmlPullParser.END_TAG -> if (parser.name.substringAfter(':').equals("response", ignoreCase = true) && insideResponse) {
                    val rawHref = href.orEmpty()
                    if (rawHref.isNotBlank()) {
                        val resolved = resolveHref(serverUrl, rawHref)
                        val fallback = resolved.substringAfterLast('/').ifBlank { "Media" }
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

    private data class WebDavEntry(
        val href: String,
        val name: String,
        val collection: Boolean,
    )
}
