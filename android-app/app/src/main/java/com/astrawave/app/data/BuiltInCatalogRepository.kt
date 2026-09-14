package com.astrawave.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves AstraWave's 150 built-in catalog definitions into live metadata.
 *
 * Catalog identity is hardcoded, but item contents are not. When the AstraWave backend is
 * configured, this repository asks it first so MDBList/TMDB credentials remain server-side.
 * Android then falls back to live Cinemeta/metadata discovery and preserves a four-hour disk cache.
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

    private val appContext = context.applicationContext
    private val preferences = BuiltInCatalogPreferences(appContext)
    // v2 prevents retired mood/theme payloads from being reused by their migration-safe service IDs.
    private val diskCache = appContext.getSharedPreferences("astrawave_builtin_catalog_cache_v2", Context.MODE_PRIVATE)

    fun load(
        catalogId: String,
        profileId: String = "default",
        limit: Int = 60,
    ): Result {
        val rawDefinition = AstraWaveBuiltInCatalogRegistry.all.firstOrNull { it.id == catalogId }
            ?: error("Unknown built-in catalog: $catalogId")
        val definition = CatalogServiceOverrides.apply(rawDefinition)
        val normalizedLimit = limit.coerceIn(1, 100)
        val cacheKey = "$catalogId:$normalizedLimit"
        val now = System.currentTimeMillis()
        cache[cacheKey]?.takeIf { now - it.loadedAt < CACHE_MS }?.let { cached ->
            return Result(definition, cached.items, cached.sourceLabel, true)
        }
        readDisk(cacheKey)?.takeIf { now - it.loadedAt < CACHE_MS }?.let { cached ->
            cache[cacheKey] = cached
            return Result(definition, cached.items, cached.sourceLabel, true)
        }

        val remote = metadata.loadBuiltInCatalog(definition.id)
        val expectedType = if (definition.mediaType == BuiltInCatalogMediaType.MOVIE) "movie" else "series"
        val remoteItems = remote?.items.orEmpty()
            .asSequence()
            .filter { item -> item.type.isBlank() || item.type.equals(expectedType, true) || (expectedType == "series" && item.type.equals("tv", true)) }
            .distinctBy { canonicalKey(it) }
            .take(normalizedLimit)
            .toList()

        val items: List<AstraWaveMetadataGateway.Item>
        val label: String
        if (remoteItems.isNotEmpty()) {
            items = remoteItems
            label = remote?.source ?: "AstraWave catalog service"
        } else {
            items = loadFallback(definition, normalizedLimit)
            label = when {
                definition.id in VerifiedMdbListCatalogs -> "MDBList mapping available • live metadata fallback"
                else -> "AstraWave live metadata"
            }
        }

        val entry = CacheEntry(now, items, label)
        cache[cacheKey] = entry
        writeDisk(cacheKey, entry)
        return Result(definition, items, label, false)
    }

    fun page(
        catalogId: String,
        profileId: String = "default",
        offset: Int = 0,
        pageSize: Int = 24,
    ): Result {
        val safeOffset = offset.coerceAtLeast(0)
        val safeSize = pageSize.coerceIn(1, 40)
        val loaded = load(catalogId, profileId, limit = 100)
        return loaded.copy(items = loaded.items.drop(safeOffset).take(safeSize))
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

        // Service overrides should never inherit the retired legacy ID's genre semantics.
        val id = if (definition.id in CatalogServiceOverrides) "service-catalog" else definition.id.substringAfter(':')
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

    private fun readDisk(cacheKey: String): CacheEntry? {
        val raw = diskCache.getString(cacheKey, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val itemsArray = root.optJSONArray("items") ?: JSONArray()
            val items = buildList {
                for (i in 0 until itemsArray.length()) {
                    val obj = itemsArray.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val name = obj.optString("name")
                    if (id.isBlank() || name.isBlank()) continue
                    add(
                        AstraWaveMetadataGateway.Item(
                            id = id,
                            type = obj.optString("type"),
                            name = name,
                            description = obj.optString("description").takeIf { it.isNotBlank() },
                            posterUrl = obj.optString("posterUrl").takeIf { it.isNotBlank() },
                            backdropUrl = obj.optString("backdropUrl").takeIf { it.isNotBlank() },
                            releaseInfo = obj.optString("releaseInfo").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
            CacheEntry(
                loadedAt = root.optLong("loadedAt", 0L),
                items = items,
                sourceLabel = root.optString("sourceLabel").ifBlank { "AstraWave cached catalog" },
            )
        }.getOrNull()
    }

    private fun writeDisk(cacheKey: String, entry: CacheEntry) {
        runCatching {
            val items = JSONArray()
            entry.items.forEach { item ->
                items.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("type", item.type)
                        .put("name", item.name)
                        .put("description", item.description ?: "")
                        .put("posterUrl", item.posterUrl ?: "")
                        .put("backdropUrl", item.backdropUrl ?: "")
                        .put("releaseInfo", item.releaseInfo ?: ""),
                )
            }
            diskCache.edit().putString(
                cacheKey,
                JSONObject()
                    .put("loadedAt", entry.loadedAt)
                    .put("sourceLabel", entry.sourceLabel)
                    .put("items", items)
                    .toString(),
            ).apply()
        }
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
