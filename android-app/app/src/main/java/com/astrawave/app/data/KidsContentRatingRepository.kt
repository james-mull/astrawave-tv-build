package com.astrawave.app.data

import com.astrawave.app.BuildConfig
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
    )

    fun rating(media: DynamicCollectionRepository.Media, itemId: String): RatingResult {
        val key = "${media.name}:$itemId"
        cache[key]?.takeIf { System.currentTimeMillis() - it.loadedAt < CACHE_MS }?.let { return it.result }

        val kind = if (media == DynamicCollectionRepository.Media.MOVIE) "movie" else "series"
        val result = if (apiBaseUrl.isBlank()) {
            RatingResult(null, "unavailable")
        } else {
            runCatching {
                val encoded = URLEncoder.encode(itemId, StandardCharsets.UTF_8.name())
                val root = JSONObject(SimpleHttp.getText("$apiBaseUrl/v1/rating/$kind/$encoded"))
                RatingResult(
                    rating = root.optString("rating").takeIf { it.isNotBlank() && it != "null" },
                    source = root.optString("source").ifBlank { "tmdb" },
                )
            }.getOrElse { RatingResult(null, "unavailable") }
        }
        cache[key] = CacheEntry(System.currentTimeMillis(), result)
        return result
    }

    private data class CacheEntry(val loadedAt: Long, val result: RatingResult)

    companion object {
        private const val CACHE_MS = 24L * 60L * 60L * 1000L
        private val cache = ConcurrentHashMap<String, CacheEntry>()
    }
}
