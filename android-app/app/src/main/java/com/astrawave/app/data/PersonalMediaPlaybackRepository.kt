package com.astrawave.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.astrawave.app.core.LibraryItemRef
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.core.PersonalMediaConnection
import com.astrawave.app.core.PersonalMediaItem
import com.astrawave.app.core.PersonalMediaProvider
import java.nio.charset.StandardCharsets

/**
 * Resolves an in-app personal-media locator into a short-lived playback plan.
 * Credentials remain in Android Keystore-backed storage and are supplied to Media3 as request
 * headers rather than being embedded in persisted URLs or AstraWave library records.
 */
class PersonalMediaPlaybackRepository(context: Context) {
    data class PlaybackPlan(
        val url: String,
        val headers: Map<String, String>,
        val libraryItem: LibraryItemRef,
    )

    private val appContext = context.applicationContext
    private val connections = PersonalMediaStore(appContext)
    private val credentials = PersonalMediaCredentialStore(appContext)

    fun locator(connection: PersonalMediaConnection, item: PersonalMediaItem): String = Uri.Builder()
        .scheme(SCHEME)
        .authority(AUTHORITY)
        .appendQueryParameter("connection", connection.id)
        .appendQueryParameter("item", encode(item.id))
        .appendQueryParameter("type", item.type.name)
        .appendQueryParameter("title", encode(item.title))
        .appendQueryParameter("poster", encode(item.posterUrl ?: item.backdropUrl ?: ""))
        .build()
        .toString()

    fun isLocator(value: String?): Boolean = value?.startsWith("$SCHEME://$AUTHORITY", ignoreCase = true) == true

    fun resolve(locator: String, profileId: String): PlaybackPlan? {
        if (!isLocator(locator)) return null
        val uri = Uri.parse(locator)
        val connectionId = uri.getQueryParameter("connection").orEmpty()
        val itemId = decode(uri.getQueryParameter("item"))
        val title = decode(uri.getQueryParameter("title")).ifBlank { "Personal Media" }
        val poster = decode(uri.getQueryParameter("poster")).takeIf(String::isNotBlank)
        val type = uri.getQueryParameter("type")
            ?.let { runCatching { LibraryMediaType.valueOf(it) }.getOrNull() }
            ?: LibraryMediaType.MOVIE
        if (connectionId.isBlank() || itemId.isBlank()) return null

        val connection = connections.load(profileId).firstOrNull { it.id == connectionId && it.enabled } ?: return null
        val headers = mutableMapOf("User-Agent" to "AstraWave/0.1")
        val url = when (connection.provider) {
            PersonalMediaProvider.PLEX -> {
                val token = credentials.loadToken(connection.id) ?: return null
                headers["X-Plex-Token"] = token
                headers["X-Plex-Product"] = "AstraWave"
                headers["X-Plex-Version"] = "0.1"
                headers["X-Plex-Client-Identifier"] = "astrawave-android"
                val metadataPath = "/library/metadata/${Uri.encode(itemId)}"
                connection.serverUrl.trimEnd('/') +
                    "/video/:/transcode/universal/start.m3u8" +
                    "?path=${Uri.encode(metadataPath)}&mediaIndex=0&partIndex=0&protocol=hls" +
                    "&directPlay=1&directStream=1&fastSeek=1&subtitleSize=100"
            }

            PersonalMediaProvider.JELLYFIN, PersonalMediaProvider.EMBY -> {
                val token = credentials.loadToken(connection.id) ?: return null
                headers["X-Emby-Token"] = token
                connection.serverUrl.trimEnd('/') + "/Videos/${Uri.encode(itemId)}/stream?static=true"
            }

            PersonalMediaProvider.WEBDAV, PersonalMediaProvider.NAS -> {
                val username = credentials.loadUsername(connection.id)
                val password = credentials.loadPassword(connection.id)
                if (!username.isNullOrBlank() || password != null) {
                    if (username.isNullOrBlank() || password == null) return null
                    val raw = "$username:$password".toByteArray(StandardCharsets.UTF_8)
                    headers["Authorization"] = "Basic ${Base64.encodeToString(raw, Base64.NO_WRAP)}"
                }
                itemId
            }
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        return PlaybackPlan(
            url = url,
            headers = headers,
            libraryItem = LibraryItemRef(
                id = "personal:${connection.id}:$itemId",
                type = type,
                title = title,
                posterUrl = poster,
                sourceId = "personal:${connection.provider.name.lowercase()}:${connection.id}:$itemId",
            ),
        )
    }

    companion object {
        private const val SCHEME = "astrawave-personal"
        private const val AUTHORITY = "play"

        private fun encode(value: String): String = Base64.encodeToString(
            value.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )

        private fun decode(value: String?): String {
            if (value.isNullOrBlank()) return ""
            return runCatching {
                Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                    .toString(StandardCharsets.UTF_8)
            }.getOrDefault("")
        }
    }
}
