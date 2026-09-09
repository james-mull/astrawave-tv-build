package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.IptvSource
import com.astrawave.app.core.IptvSourceType
import com.astrawave.app.core.ScrapeRequest
import com.astrawave.app.core.ScrapedLink
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Resolves movies and exact TV episodes from user-configured Xtream accounts.
 *
 * Xtream credentials are loaded from IptvSourceStore, where passwords are encrypted with Android
 * Keystore. AstraWave never discovers accounts or ships credentials; only enabled customer sources
 * participate. Stream URLs are kept in memory for playback and are not synced to Firestore.
 *
 * Discovery deliberately does not require a stream preflight to succeed. Many Xtream/CDN endpoints
 * reject Range/health-probe requests while still playing normally in Media3. Candidates are surfaced
 * immediately and PlayerActivity performs real playback/failover when the user presses Play.
 */
class XtreamVodSourceResolver(context: Context) {
    private val sourceStore = IptvSourceStore(context.applicationContext)
    private val catalogCache = linkedMapOf<String, CachedPayload>()

    suspend fun discover(request: ScrapeRequest, profileId: String): List<ResolvedSource> {
        val sources = sourceStore.load(profileId)
            .filter { it.enabled && it.type == IptvSourceType.XTREAM }
            .filter { !it.xtreamServer.isNullOrBlank() && !it.xtreamUsername.isNullOrBlank() && !it.xtreamPassword.isNullOrBlank() }

        return sources.flatMap { source ->
            runCatching {
                if (request.season != null && request.episode != null) discoverEpisode(source, request)
                else discoverMovie(source, request)
            }.getOrDefault(emptyList())
        }
            .distinctBy { it.link.url }
            .sortedWith(compareByDescending<ResolvedSource> { it.score }.thenBy { it.latencyMs ?: Long.MAX_VALUE })
    }

    private fun discoverMovie(source: IptvSource, request: ScrapeRequest): List<ResolvedSource> {
        val streams = JSONArray(payload(source, "get_vod_streams"))
        val matches = buildList {
            for (index in 0 until streams.length()) {
                val item = streams.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                val streamId = item.optString("stream_id").trim()
                if (name.isBlank() || streamId.isBlank()) continue
                val titleScore = XtreamVodMatcher.titleScore(request.title, name)
                if (titleScore < 70) continue
                val providerYear = item.optInt("year", 0).takeIf { it > 1800 }
                    ?: item.optString("releaseDate").take(4).toIntOrNull()
                val yearScore = XtreamVodMatcher.yearScore(request.year, providerYear)
                if (yearScore < 0) continue
                add(Triple(item, titleScore, yearScore))
            }
        }.sortedByDescending { (_, title, year) -> title + year }.take(MAX_MATCHES)

        return matches.mapNotNull { (item, titleScore, yearScore) ->
            val streamId = item.optString("stream_id")
            val extension = safeExtension(item.optString("container_extension"), "mp4")
            val url = streamUrl(source, "movie", streamId, extension) ?: return@mapNotNull null
            resolved(source, url, item.optString("name"), titleScore + yearScore)
        }
    }

    private fun discoverEpisode(source: IptvSource, request: ScrapeRequest): List<ResolvedSource> {
        val series = JSONArray(payload(source, "get_series"))
        val seriesMatches = buildList {
            for (index in 0 until series.length()) {
                val item = series.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                val seriesId = item.optString("series_id").trim()
                if (name.isBlank() || seriesId.isBlank()) continue
                val score = XtreamVodMatcher.titleScore(request.title, name)
                if (score >= 70) add(item to score)
            }
        }.sortedByDescending { it.second }.take(MAX_SERIES_MATCHES)

        return seriesMatches.flatMap { (seriesItem, titleScore) ->
            val seriesId = seriesItem.optString("series_id")
            val info = runCatching { JSONObject(payload(source, "get_series_info", mapOf("series_id" to seriesId))) }.getOrNull()
                ?: return@flatMap emptyList()
            val episode = findEpisode(info.opt("episodes"), request.season ?: return@flatMap emptyList(), request.episode ?: return@flatMap emptyList())
                ?: return@flatMap emptyList()
            val episodeId = episode.optString("id").trim()
            if (episodeId.isBlank()) return@flatMap emptyList()
            val extension = safeExtension(episode.optString("container_extension"), "mp4")
            val url = streamUrl(source, "series", episodeId, extension) ?: return@flatMap emptyList()
            val episodeTitle = episode.optString("title").ifBlank { "${seriesItem.optString("name")} S${request.season}E${request.episode}" }
            listOfNotNull(resolved(source, url, episodeTitle, titleScore + 80))
        }
    }

    private fun findEpisode(raw: Any?, season: Int, episode: Int): JSONObject? {
        val candidates = when (raw) {
            is JSONObject -> raw.optJSONArray(season.toString()) ?: JSONArray()
            is JSONArray -> raw
            else -> JSONArray()
        }
        for (index in 0 until candidates.length()) {
            val item = candidates.optJSONObject(index) ?: continue
            val seasonNumber = item.optInt("season", season)
            val episodeNumber = item.optInt("episode_num", item.optInt("episode", 0))
            if (XtreamVodMatcher.episodeMatches(season, episode, seasonNumber, episodeNumber)) return item
        }
        return null
    }

    private fun resolved(source: IptvSource, url: String, label: String, matchScore: Int): ResolvedSource {
        val health = runCatching { StreamHealthChecker.check(url) }.getOrNull()
        val quality = inferQuality(label)
        val qualityScore = when (quality) {
            "4K" -> 520
            "1440p" -> 460
            "1080p" -> 410
            "720p" -> 300
            "480p" -> 180
            else -> 100
        }
        val latencyBonus = when (val latency = health?.latencyMs) {
            null -> 0
            in 0..499 -> 90
            in 500..899 -> 70
            in 900..1_499 -> 45
            in 1_500..2_499 -> 20
            else -> 0
        }
        return ResolvedSource(
            link = ScrapedLink(
                url = url,
                sourceName = source.name,
                quality = quality,
                mimeType = health?.contentType,
                direct = true,
                licenseLabel = "Customer-authorized Xtream source",
                attribution = source.name,
            ),
            reachable = health?.reachable == true,
            latencyMs = health?.latencyMs,
            contentType = health?.contentType,
            score = 430 + matchScore + qualityScore + latencyBonus - source.priority.coerceAtLeast(0),
        )
    }

    private fun payload(source: IptvSource, action: String, extras: Map<String, String> = emptyMap()): String {
        val server = source.xtreamServer?.trim()?.trimEnd('/') ?: error("Xtream server missing")
        val username = source.xtreamUsername ?: error("Xtream username missing")
        val password = source.xtreamPassword ?: error("Xtream password missing")
        val cacheKey = buildString {
            append(source.id); append(':'); append(action)
            extras.toSortedMap().forEach { (key, value) -> append(':'); append(key); append('='); append(value) }
        }
        val now = System.currentTimeMillis()
        synchronized(catalogCache) {
            catalogCache[cacheKey]?.takeIf { now - it.loadedAtMs <= CACHE_TTL_MS }?.let { return it.payload }
        }
        val query = buildList {
            add("username=${encode(username)}")
            add("password=${encode(password)}")
            add("action=${encode(action)}")
            extras.forEach { (key, value) -> add("${encode(key)}=${encode(value)}") }
        }.joinToString("&")
        val loaded = SimpleHttp.getText("$server/player_api.php?$query")
        synchronized(catalogCache) {
            catalogCache[cacheKey] = CachedPayload(now, loaded)
            while (catalogCache.size > MAX_CACHE_ENTRIES) catalogCache.entries.firstOrNull()?.key?.let(catalogCache::remove)
        }
        return loaded
    }

    private fun streamUrl(source: IptvSource, kind: String, streamId: String, extension: String): String? {
        val server = source.xtreamServer?.trim()?.trimEnd('/') ?: return null
        val username = source.xtreamUsername ?: return null
        val password = source.xtreamPassword ?: return null
        if (streamId.isBlank()) return null
        return "$server/$kind/${encodePath(username)}/${encodePath(password)}/${encodePath(streamId)}.$extension"
    }

    private fun inferQuality(label: String): String = when {
        label.contains("2160", true) || label.contains("4k", true) || label.contains("uhd", true) -> "4K"
        label.contains("1440", true) -> "1440p"
        label.contains("1080", true) || label.contains("fhd", true) -> "1080p"
        label.contains("720", true) || label.contains(" hd", true) -> "720p"
        label.contains("480", true) -> "480p"
        else -> "Auto"
    }

    private fun safeExtension(value: String, fallback: String): String {
        val clean = value.lowercase().replace(Regex("[^a-z0-9]"), "")
        return clean.takeIf { it in setOf("mp4", "mkv", "avi", "m3u8", "ts") } ?: fallback
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    private fun encodePath(value: String): String = encode(value).replace("+", "%20")

    private data class CachedPayload(val loadedAtMs: Long, val payload: String)

    companion object {
        private const val CACHE_TTL_MS = 10L * 60L * 1000L
        private const val MAX_CACHE_ENTRIES = 24
        private const val MAX_MATCHES = 8
        private const val MAX_SERIES_MATCHES = 3
    }
}
