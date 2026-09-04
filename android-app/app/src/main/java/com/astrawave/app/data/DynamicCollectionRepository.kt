package com.astrawave.app.data

import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Zero-config dynamic movie/TV discovery for the visual Lists hubs.
 * Uses live Cinemeta catalogs with pagination/genre extras so shelves refresh without
 * requiring an app update or user-supplied metadata key. Results are cached briefly to
 * avoid hammering metadata endpoints while a user moves between collections.
 */
class DynamicCollectionRepository(
    private val gateway: AstraWaveMetadataGateway = AstraWaveMetadataGateway(),
) {
    enum class Media(val cinemetaType: String) { MOVIE("movie"), SERIES("series") }

    private data class CacheEntry(
        val loadedAt: Long,
        val items: List<AstraWaveMetadataGateway.Item>,
    )

    fun top(media: Media, pages: Int = 5): List<AstraWaveMetadataGateway.Item> =
        loadPaged(media = media, genre = null, pages = pages.coerceIn(1, 6))

    fun genre(media: Media, genre: String, pages: Int = 3): List<AstraWaveMetadataGateway.Item> =
        loadPaged(media = media, genre = genre.trim(), pages = pages.coerceIn(1, 5))

    private fun loadPaged(
        media: Media,
        genre: String?,
        pages: Int,
    ): List<AstraWaveMetadataGateway.Item> {
        val key = "${media.name}:${genre.orEmpty().lowercase()}:$pages"
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { now - it.loadedAt < CACHE_MS }?.let { return it.items }

        val pageSize = 100
        val gathered = buildList {
            repeat(pages) { page ->
                val skip = page * pageSize
                val result = runCatching { loadCinemeta(media, genre, skip) }.getOrDefault(emptyList())
                addAll(result)
                if (result.isEmpty()) return@repeat
            }
        }.distinctBy { "${it.type}:${it.id}" }

        val fallback = if (gathered.isNotEmpty()) gathered else {
            val catalog = if (media == Media.MOVIE) {
                AstraWaveMetadataGateway.Catalog.TRENDING_MOVIES
            } else {
                AstraWaveMetadataGateway.Catalog.TRENDING_SERIES
            }
            gateway.load(catalog)
        }
        cache[key] = CacheEntry(now, fallback)
        return fallback
    }

    private fun loadCinemeta(
        media: Media,
        genre: String?,
        skip: Int,
    ): List<AstraWaveMetadataGateway.Item> {
        val extras = buildList {
            genre?.takeIf { it.isNotBlank() }?.let {
                add("genre=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}")
            }
            if (skip > 0) add("skip=$skip")
        }
        val suffix = if (extras.isEmpty()) "" else "/${extras.joinToString("&")}"
        val url = "https://v3-cinemeta.strem.io/catalog/${media.cinemetaType}/top$suffix.json"
        return parseCinemeta(JSONObject(SimpleHttp.getText(url)))
    }

    private fun parseCinemeta(root: JSONObject): List<AstraWaveMetadataGateway.Item> {
        val metas = root.optJSONArray("metas") ?: return emptyList()
        return buildList {
            for (i in 0 until metas.length()) {
                val meta = metas.optJSONObject(i) ?: continue
                val id = meta.optString("id")
                val name = meta.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                add(
                    AstraWaveMetadataGateway.Item(
                        id = id,
                        type = meta.optString("type"),
                        name = name,
                        description = meta.optString("description").takeIf { it.isNotBlank() },
                        posterUrl = meta.optString("poster").takeIf { it.isNotBlank() },
                        backdropUrl = meta.optString("background").takeIf { it.isNotBlank() },
                        releaseInfo = meta.optString("releaseInfo").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }

    companion object {
        private const val CACHE_MS = 30L * 60L * 1000L
        private val cache = ConcurrentHashMap<String, CacheEntry>()
    }
}
