package com.astrawave.app.data

import com.astrawave.app.core.AudioItem
import com.astrawave.app.core.AudioItemType
import com.astrawave.app.core.AudioSubscription
import com.astrawave.app.core.RadioStation
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Zero-configuration discovery for AstraWave Radio, Music previews and Podcasts. */
class AstraWaveAudioDiscoveryRepository {
    private val radioServers = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
        "https://all.api.radio-browser.info",
    )

    val podcastTopics = listOf(
        "news", "comedy", "true crime", "sports", "business", "technology",
        "science", "history", "society culture", "education", "health fitness",
        "music", "tv film", "kids family", "arts", "fiction", "politics",
        "daily news", "entrepreneurship", "investing", "design", "food", "travel",
        "parenting", "relationships", "gaming", "automotive", "nature", "medicine",
        "self improvement",
    )

    val musicGenres = listOf(
        "pop", "rock", "hip hop", "r&b", "country", "electronic",
        "alternative", "jazz", "classical", "latin", "reggae", "metal",
        "dance", "blues", "folk", "indie", "punk", "gospel", "world",
        "afrobeats", "k-pop", "j-pop", "ambient", "house", "techno", "trance",
        "disco", "funk", "soul",
    )

    fun discoverRadio(limit: Int = 80): List<RadioStation> {
        val capped = limit.coerceIn(1, 100)
        val json = radioServers.firstNotNullOfOrNull { server ->
            runCatching {
                SimpleHttp.getText("$server/json/stations/topvote/$capped?hidebroken=true&order=votes&reverse=true")
            }.getOrNull()
        } ?: return emptyList()
        return parseRadio(json)
    }

    fun searchRadio(query: String, limit: Int = 60): List<RadioStation> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
        val capped = limit.coerceIn(1, 100)
        val json = radioServers.firstNotNullOfOrNull { server ->
            runCatching {
                SimpleHttp.getText("$server/json/stations/search?name=$encoded&hidebroken=true&limit=$capped&order=votes&reverse=true")
            }.getOrNull()
        } ?: return emptyList()
        return parseRadio(json)
    }

    private fun parseRadio(json: String): List<RadioStation> {
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

    fun discoverPodcasts(perTopic: Int = 6): List<AudioSubscription> {
        val limit = perTopic.coerceIn(1, 12)
        return podcastTopics.flatMap { topic -> searchPodcasts(topic, limit) }
            .distinctBy { it.feedUrl.lowercase() }
            .take(140)
    }

    fun searchPodcasts(query: String, limit: Int = 80): List<AudioSubscription> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
        val capped = limit.coerceIn(1, 120)
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
                add(
                    AudioSubscription(
                        id = "itunes:$collectionId",
                        title = title,
                        feedUrl = feedUrl,
                        artworkUrl = item.optString("artworkUrl600").ifBlank { item.optString("artworkUrl100") }.takeIf { it.startsWith("https://") },
                        videoCapable = false,
                    ),
                )
            }
        }.distinctBy { it.feedUrl.lowercase() }
    }

    fun discoverMusic(perGenre: Int = 6): List<AudioItem> = musicGenres
        .flatMap { genre -> searchMusic(genre, perGenre) }
        .distinctBy { it.id }
        .take(160)

    fun searchMusic(query: String, limit: Int = 90): List<AudioItem> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
        val capped = limit.coerceIn(1, 120)
        val root = runCatching {
            JSONObject(SimpleHttp.getText("https://itunes.apple.com/search?media=music&entity=song&country=US&limit=$capped&term=$encoded"))
        }.getOrNull() ?: return emptyList()
        val results = root.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val preview = item.optString("previewUrl").trim()
                val title = item.optString("trackName").trim()
                if (!preview.startsWith("https://") || title.isBlank()) continue
                val trackId = item.optLong("trackId", 0L)
                add(
                    AudioItem(
                        id = "itunes-music:${if (trackId > 0) trackId else title.hashCode()}",
                        type = AudioItemType.MUSIC,
                        title = title,
                        subtitle = listOf(item.optString("artistName"), item.optString("collectionName"), item.optString("primaryGenreName"))
                            .filter(String::isNotBlank).joinToString(" • "),
                        artworkUrl = item.optString("artworkUrl100").takeIf { it.startsWith("https://") },
                        mediaUrl = preview,
                    ),
                )
            }
        }.distinctBy { it.id }
    }
}
