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
            addon.manifest.catalogs
                .filterNot { it.id in addon.hiddenCatalogIds }
                .sortedWith(
                    compareByDescending<StremioCatalogDescriptor> { it.id in addon.pinnedCatalogIds }
                        .thenBy { it.name.lowercase() },
                )
                .map { catalog ->
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

    private fun eligibleAddons(profileId: String): List<InstalledAddon> = store.load(profileId)
        .filter { it.enabled }
        .filter { StremioResource.CATALOG in it.manifest.resources || it.manifest.catalogs.isNotEmpty() }
}
