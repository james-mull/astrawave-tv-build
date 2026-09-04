package com.astrawave.app.data

import com.astrawave.app.core.AudioSubscription
import com.astrawave.app.core.RadioStation
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Zero-configuration discovery for AstraWave Radio and Podcasts.
 *
 * Radio uses the public Radio Browser network. Podcast discovery uses the public
 * iTunes Search catalog only to locate canonical podcast RSS feeds; episode playback
 * continues to come directly from each publisher's RSS enclosure URLs.
 */
class AstraWaveAudioDiscoveryRepository {
    private val radioServers = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
    )

    private val podcastTopics = listOf(
        "news",
        "comedy",
        "true crime",
        "technology",
        "sports",
        "business",
        "science",
        "history",
    )

    fun discoverRadio(limit: Int = 60): List<RadioStation> {
        val capped = limit.coerceIn(1, 100)
        val json = radioServers.firstNotNullOfOrNull { server ->
            runCatching {
                SimpleHttp.getText("$server/json/stations/topvote/$capped?hidebroken=true&order=votes&reverse=true")
            }.getOrNull()
        } ?: return emptyList()

        val stations = JSONArray(json)
        return buildList {
            for (i in 0 until stations.length()) {
                val item = stations.optJSONObject(i) ?: continue
                val id = item.optString("stationuuid")
                val name = item.optString("name").trim()
                val streamUrl = item.optString("url_resolved").ifBlank { item.optString("url") }.trim()
                if (id.isBlank() || name.isBlank() || !streamUrl.startsWith("https://")) continue
                add(
                    RadioStation(
                        id = "radio-browser:$id",
                        name = name,
                        streamUrl = streamUrl,
                        genre = item.optString("tags").split(',').firstOrNull()?.trim()?.takeIf { it.isNotBlank() },
                        country = item.optString("countrycode").takeIf { it.isNotBlank() },
                        logoUrl = item.optString("favicon").takeIf { it.startsWith("https://") },
                    ),
                )
            }
        }.distinctBy { it.id }
    }

    fun discoverPodcasts(perTopic: Int = 5): List<AudioSubscription> {
        val limit = perTopic.coerceIn(1, 12)
        return podcastTopics.flatMap { topic ->
            val encoded = URLEncoder.encode(topic, StandardCharsets.UTF_8.name())
            val json = runCatching {
                SimpleHttp.getText("https://itunes.apple.com/search?media=podcast&entity=podcast&country=US&limit=$limit&term=$encoded")
            }.getOrNull() ?: return@flatMap emptyList()

            val root = JSONObject(json)
            val results = root.optJSONArray("results") ?: return@flatMap emptyList()
            buildList {
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val feedUrl = item.optString("feedUrl").trim()
                    val title = item.optString("collectionName").ifBlank { item.optString("trackName") }.trim()
                    val collectionId = item.optLong("collectionId", 0L)
                    if (feedUrl.isBlank() || title.isBlank() || collectionId <= 0L || !feedUrl.startsWith("https://")) continue
                    add(
                        AudioSubscription(
                            id = "itunes:$collectionId",
                            title = title,
                            feedUrl = feedUrl,
                            artworkUrl = item.optString("artworkUrl600")
                                .ifBlank { item.optString("artworkUrl100") }
                                .takeIf { it.startsWith("https://") },
                            videoCapable = false,
                        ),
                    )
                }
            }
        }.distinctBy { it.feedUrl.lowercase() }
    }

    fun searchRadio(query: String, limit: Int = 40): List<RadioStation> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
        val capped = limit.coerceIn(1, 100)
        val json = radioServers.firstNotNullOfOrNull { server ->
            runCatching {
                SimpleHttp.getText("$server/json/stations/search?name=$encoded&hidebroken=true&limit=$capped&order=votes&reverse=true")
            }.getOrNull()
        } ?: return emptyList()
        val stations = JSONArray(json)
        return buildList {
            for (i in 0 until stations.length()) {
                val item = stations.optJSONObject(i) ?: continue
                val id = item.optString("stationuuid")
                val name = item.optString("name").trim()
                val streamUrl = item.optString("url_resolved").ifBlank { item.optString("url") }.trim()
                if (id.isBlank() || name.isBlank() || !streamUrl.startsWith("https://")) continue
                add(RadioStation("radio-browser:$id", name, streamUrl, item.optString("tags").split(',').firstOrNull()?.trim(), item.optString("countrycode").takeIf { it.isNotBlank() }, item.optString("favicon").takeIf { it.startsWith("https://") }))
            }
        }.distinctBy { it.id }
    }

    fun searchPodcasts(query: String, limit: Int = 30): List<AudioSubscription> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
        val capped = limit.coerceIn(1, 50)
        val root = runCatching {
            JSONObject(SimpleHttp.getText("https://itunes.apple.com/search?media=podcast&entity=podcast&country=US&limit=$capped&term=$encoded"))
        }.getOrNull() ?: return emptyList()
        val results = root.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val feedUrl = item.optString("feedUrl").trim()
                val title = item.optString("collectionName").ifBlank { item.optString("trackName") }.trim()
                val collectionId = item.optLong("collectionId", 0L)
                if (feedUrl.isBlank() || title.isBlank() || collectionId <= 0L || !feedUrl.startsWith("https://")) continue
                add(AudioSubscription("itunes:$collectionId", title, feedUrl, item.optString("artworkUrl600").ifBlank { item.optString("artworkUrl100") }.takeIf { it.startsWith("https://") }))
            }
        }.distinctBy { it.feedUrl.lowercase() }
    }
}
