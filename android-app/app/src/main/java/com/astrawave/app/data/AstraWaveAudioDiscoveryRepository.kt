package com.astrawave.app.data

import com.astrawave.app.core.AudioItem
import com.astrawave.app.core.AudioItemType
import com.astrawave.app.core.AudioSubscription
import com.astrawave.app.core.RadioStation
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Large, zero-configuration discovery layer for AstraWave Radio, music previews and podcasts.
 * Full commercial music playback is intentionally delegated to licensed/user-authorized providers;
 * this repository provides broad searchable discovery plus legal preview playback.
 *
 * When ASTRAWAVE_API_BASE_URL is configured, server-side catalogs (for example Podcast Index)
 * are merged ahead of the public fallback directories without exposing provider secrets on-device.
 */
class AstraWaveAudioDiscoveryRepository {
    private val backend = BackendAudioDirectoryRepository()
    private val radioServers = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
        "https://all.api.radio-browser.info",
    )

    val podcastTopics = listOf(
        "news", "comedy", "true crime", "sports", "business", "technology", "science", "history",
        "society culture", "education", "health fitness", "music", "tv film", "kids family", "arts",
        "fiction", "politics", "daily news", "entrepreneurship", "investing", "design", "food", "travel",
        "parenting", "relationships", "gaming", "automotive", "nature", "medicine", "self improvement",
        "religion spirituality", "government", "books", "film reviews", "basketball", "football", "baseball",
        "hockey", "soccer", "wrestling", "mma", "motorsports", "cryptocurrency", "personal finance",
        "careers", "marketing", "startups", "artificial intelligence", "cybersecurity", "space", "psychology",
        "mental health", "fitness", "nutrition", "running", "outdoors", "photography", "fashion", "home",
        "gardening", "diy", "language learning", "documentary", "audio drama",
    ).distinct()

    val musicGenres = listOf(
        "pop", "rock", "hip hop", "rap", "r&b", "country", "electronic", "alternative", "jazz", "classical",
        "latin", "reggae", "metal", "dance", "blues", "folk", "indie", "punk", "gospel", "world",
        "afrobeats", "k-pop", "j-pop", "ambient", "house", "techno", "trance", "disco", "funk", "soul",
        "americana", "bluegrass", "christian", "soundtrack", "new age", "opera", "singer songwriter",
        "hard rock", "indie pop", "indie rock", "edm", "dubstep", "drum and bass", "lofi", "chill",
        "salsa", "bachata", "reggaeton", "regional mexican", "brazilian", "african", "arabic", "bollywood",
    ).distinct()

    val radioGenres = listOf(
        "news", "talk", "sports", "pop", "rock", "hip hop", "r&b", "country", "jazz", "classical",
        "electronic", "dance", "house", "techno", "metal", "alternative", "indie", "oldies", "80s", "90s",
        "latin", "reggae", "world", "religious", "gospel", "public radio", "college", "community", "ambient",
    )

    val radioCountries = listOf(
        "US", "CA", "MX", "GB", "IE", "FR", "DE", "ES", "IT", "NL", "BE", "CH", "AT", "PT",
        "SE", "NO", "DK", "FI", "PL", "CZ", "RO", "GR", "TR", "AU", "NZ", "JP", "KR", "IN",
        "BR", "AR", "CL", "CO", "PE", "ZA", "NG", "KE", "EG", "AE", "IL", "PH", "ID", "SG",
    )

    fun discoverRadio(limit: Int = 200, offset: Int = 0): List<RadioStation> {
        val remote = if (backend.configured) backend.radio(limit = limit, offset = offset) else emptyList()
        val fallback = radioRequest(
            "/json/stations/topvote/${limit.coerceIn(1, 500)}?hidebroken=true&order=votes&reverse=true&offset=${offset.coerceAtLeast(0)}",
        )
        return mergeRadio(remote, fallback, limit)
    }

    fun radioByGenre(genre: String, limit: Int = 200, offset: Int = 0): List<RadioStation> {
        val remote = if (backend.configured) backend.radio(genre = genre, limit = limit, offset = offset) else emptyList()
        val tag = URLEncoder.encode(genre.trim(), StandardCharsets.UTF_8.name())
        val fallback = radioRequest(
            "/json/stations/bytag/$tag?hidebroken=true&order=votes&reverse=true&limit=${limit.coerceIn(1, 500)}&offset=${offset.coerceAtLeast(0)}",
        )
        return mergeRadio(remote, fallback, limit)
    }

    fun radioByCountry(countryCode: String, limit: Int = 200, offset: Int = 0): List<RadioStation> {
        val remote = if (backend.configured) backend.radio(country = countryCode, limit = limit, offset = offset) else emptyList()
        val code = URLEncoder.encode(countryCode.trim().uppercase(), StandardCharsets.UTF_8.name())
        val fallback = radioRequest(
            "/json/stations/bycountrycodeexact/$code?hidebroken=true&order=votes&reverse=true&limit=${limit.coerceIn(1, 500)}&offset=${offset.coerceAtLeast(0)}",
        )
        return mergeRadio(remote, fallback, limit)
    }

    fun searchRadio(query: String, limit: Int = 250, offset: Int = 0): List<RadioStation> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val remote = if (backend.configured) backend.radio(query = q, limit = limit, offset = offset) else emptyList()
        val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
        val fallback = radioRequest(
            "/json/stations/search?name=$encoded&hidebroken=true&limit=${limit.coerceIn(1, 500)}&offset=${offset.coerceAtLeast(0)}&order=votes&reverse=true",
        )
        return mergeRadio(remote, fallback, limit)
    }

    private fun mergeRadio(primary: List<RadioStation>, fallback: List<RadioStation>, limit: Int): List<RadioStation> =
        (primary + fallback).distinctBy { it.streamUrl.lowercase() }.take(limit.coerceIn(1, 500))

    private fun radioRequest(path: String): List<RadioStation> {
        val json = radioServers.firstNotNullOfOrNull { server ->
            runCatching { SimpleHttp.getText("$server$path") }.getOrNull()
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

    fun discoverPodcasts(perTopic: Int = 10, maxItems: Int = 500): List<AudioSubscription> {
        val target = maxItems.coerceIn(1, 1_000)
        val server = if (backend.configured) backend.podcasts(limit = target.coerceAtMost(300)) else emptyList()
        val limit = perTopic.coerceIn(1, 20)
        val fallback = podcastTopics.asSequence()
            .flatMap { topic -> applePodcastSearch(topic, limit).asSequence() }
            .distinctBy { it.feedUrl.lowercase() }
            .take(target)
            .toList()
        return (server + fallback).distinctBy { it.feedUrl.lowercase() }.take(target)
    }

    fun podcastsByTopic(topic: String, limit: Int = 120): List<AudioSubscription> = searchPodcasts(topic, limit)

    fun searchPodcasts(query: String, limit: Int = 150): List<AudioSubscription> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val target = limit.coerceIn(1, 300)
        val server = if (backend.configured) backend.podcasts(q, target) else emptyList()
        val fallback = applePodcastSearch(q, target.coerceAtMost(200))
        return (server + fallback).distinctBy { it.feedUrl.lowercase() }.take(target)
    }

    private fun applePodcastSearch(query: String, limit: Int): List<AudioSubscription> {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val capped = limit.coerceIn(1, 200)
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
                        artworkUrl = item.optString("artworkUrl600").ifBlank { item.optString("artworkUrl100") }
                            .takeIf { it.startsWith("https://") },
                        videoCapable = false,
                    ),
                )
            }
        }.distinctBy { it.feedUrl.lowercase() }
    }

    fun discoverMusic(perGenre: Int = 10, maxItems: Int = 500): List<AudioItem> = musicGenres.asSequence()
        .flatMap { genre -> searchMusic(genre, perGenre).asSequence() }
        .distinctBy { it.id }
        .take(maxItems.coerceIn(1, 1_000))
        .toList()

    fun musicByGenre(genre: String, limit: Int = 150): List<AudioItem> = searchMusic(genre, limit)

    fun searchMusic(query: String, limit: Int = 150): List<AudioItem> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val target = limit.coerceIn(1, 300)
        val server = if (backend.configured) backend.music(q, target) else emptyList()
        val fallback = appleMusicSearch(q, target.coerceAtMost(200))
        return (server + fallback).distinctBy { it.id }.take(target)
    }

    private fun appleMusicSearch(query: String, limit: Int): List<AudioItem> {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val capped = limit.coerceIn(1, 200)
        val root = runCatching {
            JSONObject(SimpleHttp.getText("https://itunes.apple.com/search?media=music&entity=song&country=US&limit=$capped&term=$encoded"))
        }.getOrNull() ?: return emptyList()
        val results = root.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val preview = item.optString("previewUrl").trim()
                val title = item.optString("trackName").trim()
                if (title.isBlank()) continue
                val trackId = item.optLong("trackId", 0L)
                add(
                    AudioItem(
                        id = "itunes-music:${if (trackId > 0) trackId else title.hashCode()}",
                        type = AudioItemType.MUSIC,
                        title = title,
                        subtitle = listOf(
                            item.optString("artistName"),
                            item.optString("collectionName"),
                            item.optString("primaryGenreName"),
                        ).filter(String::isNotBlank).joinToString(" • "),
                        artworkUrl = item.optString("artworkUrl100").takeIf { it.startsWith("https://") },
                        mediaUrl = preview.takeIf { it.startsWith("https://") },
                    ),
                )
            }
        }.distinctBy { it.id }
    }
}
