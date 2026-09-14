package com.astrawave.app.data

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves AstraWave's 150 built-in catalog definitions into live metadata.
 *
 * Catalog identity is hardcoded, but item contents are not. Verified MDBList-backed definitions
 * can be fulfilled by the AstraWave backend when configured; otherwise this repository falls back
 * to live Cinemeta/metadata discovery so every catalog remains useful without shipping stale titles.
 */
class BuiltInCatalogRepository(
    context: Context,
    private val metadata: AstraWaveMetadataGateway = AstraWaveMetadataGateway(),
    private val dynamic: DynamicCollectionRepository = DynamicCollectionRepository(metadata),
) {
    data class Result(
        val definition: BuiltInCatalogDefinition,
        val items: List<AstraWaveMetadataGateway.Item>,
        val sourceLabel: String,
        val fromCache: Boolean,
    )

    private val preferences = BuiltInCatalogPreferences(context)

    fun load(
        catalogId: String,
        profileId: String = "default",
        limit: Int = 60,
    ): Result {
        val definition = AstraWaveBuiltInCatalogRegistry.all.firstOrNull { it.id == catalogId }
            ?: error("Unknown built-in catalog: $catalogId")
        val cacheKey = "$catalogId:${limit.coerceIn(1, 100)}"
        val now = System.currentTimeMillis()
        cache[cacheKey]?.takeIf { now - it.loadedAt < CACHE_MS }?.let { cached ->
            return Result(definition, cached.items, cached.sourceLabel, true)
        }

        val items = loadFallback(definition, limit.coerceIn(1, 100))
        val label = when {
            definition.documentedUrl != null -> "MDBList-backed • live metadata fallback"
            else -> "AstraWave live metadata"
        }
        cache[cacheKey] = CacheEntry(now, items, label)
        return Result(definition, items, label, false)
    }

    fun visibleDefinitions(
        mediaType: BuiltInCatalogMediaType,
        profileId: String = "default",
    ): List<BuiltInCatalogDefinition> = preferences.visible(mediaType, profileId)

    fun homeDefinitions(
        mediaType: BuiltInCatalogMediaType,
        profileId: String = "default",
        maxRows: Int = 10,
    ): List<BuiltInCatalogDefinition> = preferences.visible(mediaType, profileId)
        .filter { it.featured || preferences.isPinned(profileId, it.id) }
        .sortedWith(
            compareByDescending<BuiltInCatalogDefinition> { preferences.isPinned(profileId, it.id) }
                .thenByDescending { it.featured }
                .thenBy { preferences.order(profileId, it.id) },
        )
        .take(maxRows.coerceIn(1, 16))

    private fun loadFallback(
        definition: BuiltInCatalogDefinition,
        limit: Int,
    ): List<AstraWaveMetadataGateway.Item> {
        val media = if (definition.mediaType == BuiltInCatalogMediaType.MOVIE) {
            DynamicCollectionRepository.Media.MOVIE
        } else {
            DynamicCollectionRepository.Media.SERIES
        }

        val id = definition.id.substringAfter(':')
        val genre = genreFor(id)
        val items = when {
            id.contains("popular") || id.contains("trending") || id.contains("most-watched") || id.contains("top-rated") ->
                dynamic.top(media, pages = 3)
            !genre.isNullOrBlank() -> dynamic.genre(media, genre, pages = 3)
            else -> metadata.search(definition.fallbackQuery)
        }

        val expectedType = if (definition.mediaType == BuiltInCatalogMediaType.MOVIE) "movie" else "series"
        return items
            .asSequence()
            .filter { item -> item.type.isBlank() || item.type.equals(expectedType, true) || (expectedType == "series" && item.type.equals("tv", true)) }
            .distinctBy { canonicalKey(it) }
            .take(limit)
            .toList()
    }

    private fun genreFor(id: String): String? = when {
        id.contains("action") -> "Action"
        id.contains("adventure") -> "Adventure"
        id.contains("animation") || id.contains("anime") -> "Animation"
        id.contains("comedy") -> "Comedy"
        id.contains("crime") || id.contains("detective") -> "Crime"
        id.contains("document") || id.contains("true-crime") -> "Documentary"
        id.contains("drama") -> "Drama"
        id.contains("family") || id.contains("kids") -> "Family"
        id.contains("fantasy") -> "Fantasy"
        id.contains("horror") || id.contains("halloween") -> "Horror"
        id.contains("mystery") -> "Mystery"
        id.contains("romance") || id.contains("date-night") -> "Romance"
        id.contains("sci-fi") || id.contains("science-fiction") || id.contains("space") -> "Sci-Fi"
        id.contains("thriller") || id.contains("mind-bender") || id.contains("psychological") -> "Thriller"
        id.contains("war") -> "War"
        id.contains("western") -> "Western"
        else -> null
    }

    private fun canonicalKey(item: AstraWaveMetadataGateway.Item): String =
        if (item.id.isNotBlank()) "${item.type.lowercase()}:${item.id.lowercase()}"
        else "${item.type.lowercase()}:${item.name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()}"

    private data class CacheEntry(
        val loadedAt: Long,
        val items: List<AstraWaveMetadataGateway.Item>,
        val sourceLabel: String,
    )

    companion object {
        private const val CACHE_MS = 4L * 60L * 60L * 1000L
        private val cache = ConcurrentHashMap<String, CacheEntry>()
    }
}
