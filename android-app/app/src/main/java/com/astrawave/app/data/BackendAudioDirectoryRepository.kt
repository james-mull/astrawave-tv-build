package com.astrawave.app.data

import com.astrawave.app.BuildConfig
import com.astrawave.app.core.AudioItem
import com.astrawave.app.core.AudioItemType
import com.astrawave.app.core.AudioSubscription
import com.astrawave.app.core.RadioStation
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Optional bridge to the AstraWave server-side audio directory.
 * This keeps credentialed providers (for example Podcast Index) off-device while allowing the
 * Android TV/phone/tablet app to use the same catalog breadth as web. Local public-directory
 * repositories remain the fallback whenever the backend is not configured or unavailable.
 */
class BackendAudioDirectoryRepository {
    private val baseUrl = BuildConfig.ASTRAWAVE_API_BASE_URL.trim().trimEnd('/')

    val configured: Boolean get() = baseUrl.startsWith("https://") || baseUrl.startsWith("http://")

    fun podcasts(query: String = "", limit: Int = 200): List<AudioSubscription> {
        val items = request("podcast", query = query, limit = limit) ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val feedUrl = item.optString("feedUrl").trim()
                val title = item.optString("title").trim()
                if (feedUrl.isBlank() || title.isBlank()) continue
                add(
                    AudioSubscription(
                        id = item.optString("id").ifBlank { "backend-podcast:${feedUrl.hashCode()}" },
                        title = title,
                        feedUrl = feedUrl,
                        artworkUrl = item.optString("posterUrl").takeIf { it.startsWith("http") },
                        videoCapable = item.optString("medium").equals("video", ignoreCase = true),
                    ),
                )
            }
        }.distinctBy { it.feedUrl.lowercase() }
    }

    fun music(query: String, limit: Int = 200): List<AudioItem> {
        if (query.isBlank()) return emptyList()
        val items = request("music", query = query, limit = limit) ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                if (title.isBlank()) continue
                add(
                    AudioItem(
                        id = item.optString("id").ifBlank { "backend-music:${title.hashCode()}" },
                        type = AudioItemType.MUSIC,
                        title = title,
                        subtitle = item.optString("subtitle").takeIf { it.isNotBlank() },
                        artworkUrl = item.optString("posterUrl").takeIf { it.startsWith("http") },
                        mediaUrl = item.optString("streamUrl").takeIf { it.startsWith("http") },
                    ),
                )
            }
        }.distinctBy { it.id }
    }

    fun radio(query: String = "", genre: String = "", country: String = "", limit: Int = 250, offset: Int = 0): List<RadioStation> {
        val items = request("radio", query = query, genre = genre, country = country, limit = limit, offset = offset)
            ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val streamUrl = item.optString("streamUrl").trim()
                val title = item.optString("title").trim()
                if (streamUrl.isBlank() || title.isBlank()) continue
                add(
                    RadioStation(
                        id = item.optString("id").ifBlank { "backend-radio:${streamUrl.hashCode()}" },
                        name = title,
                        streamUrl = streamUrl,
                        genre = item.optString("genre").takeIf { it.isNotBlank() },
                        country = item.optString("country").takeIf { it.isNotBlank() },
                        logoUrl = item.optString("posterUrl").takeIf { it.startsWith("http") },
                    ),
                )
            }
        }.distinctBy { it.id }
    }

    private fun request(
        kind: String,
        query: String = "",
        genre: String = "",
        country: String = "",
        limit: Int,
        offset: Int = 0,
    ) = if (!configured) null else runCatching {
        val params = mutableListOf(
            "kind=${encode(kind)}",
            "limit=${limit.coerceIn(1, 500)}",
            "offset=${offset.coerceAtLeast(0)}",
        )
        if (query.isNotBlank()) params += "q=${encode(query)}"
        if (genre.isNotBlank()) params += "tag=${encode(genre)}"
        if (country.isNotBlank()) params += "country=${encode(country.uppercase())}"
        val json = SimpleHttp.getText("$baseUrl/api/astrawave/audio-directory?${params.joinToString("&")}")
        JSONObject(json).optJSONArray("items")
    }.getOrNull()

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
