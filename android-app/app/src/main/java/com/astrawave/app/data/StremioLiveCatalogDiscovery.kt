package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.StremioMetaItem

/**
 * Metadata-only bridge for hardcoded Stremio channel catalogs.
 *
 * This lets Live TV, Sports, Search and diagnostics see channel names from the
 * customer-supplied USA TV manifest without treating unreviewed stream responses
 * as authorized playback. Playback remains in the normal eligibility pipeline.
 */
data class StremioLiveDiscoveryItem(
    val addonId: String,
    val addonName: String,
    val catalogId: String,
    val id: String,
    val name: String,
    val description: String?,
    val posterUrl: String?,
    val backgroundUrl: String?,
)

class StremioLiveCatalogDiscovery(
    context: Context,
    private val aggregator: StremioCatalogAggregator = StremioCatalogAggregator(context),
) {
    fun channels(profileId: String = "default", limit: Int = 500): List<StremioLiveDiscoveryItem> =
        aggregator.load(profileId, maxItemsPerCatalog = 100)
            .asSequence()
            .filter { row ->
                row.catalog.type.equals("tv", ignoreCase = true) ||
                    row.catalog.type.equals("channel", ignoreCase = true) ||
                    row.items.any(::looksLikeLiveChannel)
            }
            .flatMap { row ->
                row.items.asSequence().map { item ->
                    StremioLiveDiscoveryItem(
                        addonId = row.addonId,
                        addonName = row.addonName,
                        catalogId = row.catalog.id,
                        id = item.id,
                        name = item.name,
                        description = item.description,
                        posterUrl = item.posterUrl,
                        backgroundUrl = item.backgroundUrl,
                    )
                }
            }
            .distinctBy { "${it.addonId}:${it.id}" }
            .take(limit)
            .toList()

    fun sportsChannelNames(profileId: String = "default"): List<String> =
        channels(profileId)
            .asSequence()
            .filter { item -> looksSportsRelated(item.name, item.description) }
            .map { it.name }
            .distinct()
            .sorted()
            .toList()

    private fun looksLikeLiveChannel(item: StremioMetaItem): Boolean =
        item.type.equals("tv", ignoreCase = true) || item.type.equals("channel", ignoreCase = true)

    private fun looksSportsRelated(name: String, description: String?): Boolean {
        val value = "$name ${description.orEmpty()}".lowercase()
        return SPORTS_MARKERS.any(value::contains)
    }

    companion object {
        private val SPORTS_MARKERS = listOf(
            "espn", "sports", "nfl", "nba", "mlb", "nhl", "ncaa", "football",
            "basketball", "baseball", "hockey", "soccer", "f1", "formula 1", "racing",
            "ufc", "boxing", "wrestling", "golf", "tennis", "fox sports", "fs1", "fs2",
            "cbs sports", "nbc sports", "bein", "tnt sports", "big ten", "sec network",
        )
    }
}
