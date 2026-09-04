package com.astrawave.app.data

import com.astrawave.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/** Resolves US movie/TV content certifications through AstraWave's server-side metadata proxy. */
class KidsContentRatingRepository(
    private val apiBaseUrl: String = BuildConfig.ASTRAWAVE_API_BASE_URL.trimEnd('/'),
) {
    data class RatingResult(
        val rating: String?,
        val source: String,
        val resolvedTmdbId: String? = null,
    )

    fun rating(media: DynamicCollectionRepository.Media, itemId: String): RatingResult =
        ratings(media, listOf(itemId))[itemId] ?: RatingResult(null, "unavailable")

    /**
     * Resolves up to 40 uncached IDs with one AstraWave backend request. Cached values are
     * reused for 24 hours, substantially reducing round trips while browsing Kids rows/search.
     */
    fun ratings(
        media: DynamicCollectionRepository.Media,
        itemIds: List<String>,
    ): Map<String, RatingResult> {
        val uniqueIds = itemIds.map(String::trim).filter(String::isNotBlank).distinct().take(40)
        if (uniqueIds.isEmpty()) return emptyMap()

        val now = System.currentTimeMillis()
        val result = linkedMapOf<String, RatingResult>()
        val missing = mutableListOf<String>()
        uniqueIds.forEach { id ->
            val key = cacheKey(media, id)
            val cached = cache[key]
            if (cached != null && now - cached.loadedAt < CACHE_MS) result[id] = cached.result
            else missing += id
        }

        if (missing.isNotEmpty()) {
            val loaded = loadBatch(media, missing)
            missing.forEach { id ->
                val value = loaded[id] ?: RatingResult(null, "unavailable")
                cache[cacheKey(media, id)] = CacheEntry(now, value)
                result[id] = value
            }
        }
        return result
    }

    private fun loadBatch(
        media: DynamicCollectionRepository.Media,
        ids: List<String>,
    ): Map<String, RatingResult> {
        if (apiBaseUrl.isBlank()) return emptyMap()
        val kind = if (media == DynamicCollectionRepository.Media.MOVIE) "movie" else "series"
        return runCatching {
            val encodedIds = ids.joinToString(",") { URLEncoder.encode(it, StandardCharsets.UTF_8.name()) }
            val array = JSONArray(SimpleHttp.getText("$apiBaseUrl/v1/ratings/$kind?ids=$encodedIds"))
            buildMap {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    put(
                        id,
                        RatingResult(
                            rating = item.optString("rating").takeIf { it.isNotBlank() && it != "null" },
                            source = item.optString("source").ifBlank { "tmdb" },
                            resolvedTmdbId = item.optString("tmdbId").takeIf { it.isNotBlank() && it != "null" },
                        ),
                    )
                }
            }
        }.getOrElse {
            // Compatibility fallback for older backend deployments while the batch route rolls out.
            ids.associateWith { id -> loadSingle(media, id) }
        }
    }

    private fun loadSingle(media: DynamicCollectionRepository.Media, itemId: String): RatingResult {
        if (apiBaseUrl.isBlank()) return RatingResult(null, "unavailable")
        val kind = if (media == DynamicCollectionRepository.Media.MOVIE) "movie" else "series"
        return runCatching {
            val encoded = URLEncoder.encode(itemId, StandardCharsets.UTF_8.name())
            val root = JSONObject(SimpleHttp.getText("$apiBaseUrl/v1/rating/$kind/$encoded"))
            RatingResult(
                rating = root.optString("rating").takeIf { it.isNotBlank() && it != "null" },
                source = root.optString("source").ifBlank { "tmdb" },
                resolvedTmdbId = root.optString("tmdbId").takeIf { it.isNotBlank() && it != "null" },
            )
        }.getOrElse { RatingResult(null, "unavailable") }
    }

    private fun cacheKey(media: DynamicCollectionRepository.Media, itemId: String) = "${media.name}:$itemId"

    private data class CacheEntry(val loadedAt: Long, val result: RatingResult)

    companion object {
        private const val CACHE_MS = 24L * 60L * 60L * 1000L
        private val cache = ConcurrentHashMap<String, CacheEntry>()
    }
}
