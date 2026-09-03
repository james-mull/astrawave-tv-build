package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.InstalledAddon
import com.astrawave.app.core.StremioCatalogDescriptor
import com.astrawave.app.core.StremioMetaItem
import com.astrawave.app.core.StremioResource

/** One normalized catalog row contributed by a user-installed Stremio-compatible addon. */
data class StremioCatalogRow(
    val addonId: String,
    val addonName: String,
    val catalog: StremioCatalogDescriptor,
    val items: List<StremioMetaItem>,
    val error: String? = null,
)

data class StremioSearchHit(
    val addonId: String,
    val addonName: String,
    val catalogId: String,
    val catalogName: String,
    val item: StremioMetaItem,
)

/**
 * Loads metadata-only catalog rows from enabled, profile-eligible addons.
 * Stream candidates are intentionally not resolved here; playback remains behind
 * StremioEligibility and the gateway's explicit authorization callback.
 */
class StremioCatalogAggregator(
    context: Context,
    private val gateway: StremioHttpGateway = StremioHttpGateway(),
) {
    private val store = StremioAddonStore(context)

    fun load(profileId: String = "default", maxItemsPerCatalog: Int = 24): List<StremioCatalogRow> {
        return eligibleAddons(profileId).flatMap { addon ->
            visibleCatalogs(addon).map { catalog ->
                val result = runCatching { gateway.loadCatalog(addon, catalog).take(maxItemsPerCatalog) }
                StremioCatalogRow(
                    addonId = addon.manifest.id,
                    addonName = addon.manifest.name,
                    catalog = catalog,
                    items = result.getOrDefault(emptyList()),
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    /**
     * Searches enabled addon catalogs without resolving streams. If a catalog declares a
     * Stremio `search` extra, the query is sent to that catalog. Otherwise AstraWave loads a
     * small metadata page and filters names/descriptions locally so non-search catalogs can
     * still contribute useful matches.
     */
    fun search(
        query: String,
        profileId: String = "default",
        maxItemsPerCatalog: Int = 40,
        maxResults: Int = 80,
    ): List<StremioSearchHit> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return emptyList()
        val needle = normalizedQuery.lowercase()

        return eligibleAddons(profileId).flatMap { addon ->
            visibleCatalogs(addon).flatMap { catalog ->
                val extra = if (catalog.extra.any { it.equals("search", ignoreCase = true) }) {
                    mapOf("search" to normalizedQuery)
                } else {
                    emptyMap()
                }
                val items = runCatching {
                    gateway.loadCatalog(addon, catalog, extra).take(maxItemsPerCatalog)
                }.getOrDefault(emptyList())

                items.asSequence()
                    .filter { item ->
                        extra.isNotEmpty() ||
                            item.name.lowercase().contains(needle) ||
                            item.description.orEmpty().lowercase().contains(needle) ||
                            item.releaseInfo.orEmpty().lowercase().contains(needle)
                    }
                    .map { item ->
                        StremioSearchHit(
                            addonId = addon.manifest.id,
                            addonName = addon.manifest.name,
                            catalogId = catalog.id,
                            catalogName = catalog.name,
                            item = item,
                        )
                    }
                    .toList()
            }
        }.distinctBy { "${it.addonId}:${it.item.type}:${it.item.id}" }
            .take(maxResults)
    }

    private fun visibleCatalogs(addon: InstalledAddon): List<StremioCatalogDescriptor> =
        addon.manifest.catalogs
            .filterNot { it.id in addon.hiddenCatalogIds }
            .sortedWith(
                compareByDescending<StremioCatalogDescriptor> { it.id in addon.pinnedCatalogIds }
                    .thenBy { it.name.lowercase() },
            )

    private fun eligibleAddons(profileId: String): List<InstalledAddon> = store.load(profileId)
        .filter { it.enabled }
        .filter { StremioResource.CATALOG in it.manifest.resources || it.manifest.catalogs.isNotEmpty() }
}
