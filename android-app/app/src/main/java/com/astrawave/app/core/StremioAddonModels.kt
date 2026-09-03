package com.astrawave.app.core

/**
 * AstraWave-facing contract for Stremio-compatible addons. This keeps addon catalogs,
 * metadata, subtitles and eligible playback candidates behind one normalized model.
 */
data class StremioAddonManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val resources: Set<StremioResource> = emptySet(),
    val catalogs: List<StremioCatalogDescriptor> = emptyList(),
    val types: Set<String> = emptySet(),
    val baseUrl: String,
)

enum class StremioResource {
    CATALOG,
    META,
    STREAM,
    SUBTITLES,
}

data class StremioCatalogDescriptor(
    val id: String,
    val type: String,
    val name: String,
    val extra: List<String> = emptyList(),
)

data class StremioMetaItem(
    val id: String,
    val type: String,
    val name: String,
    val posterUrl: String? = null,
    val backgroundUrl: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
)

data class StremioSubtitleCandidate(
    val id: String,
    val language: String,
    val url: String,
)

data class StremioStreamCandidate(
    val addonId: String,
    val name: String,
    val title: String? = null,
    val url: String? = null,
    val externalUrl: String? = null,
    val behaviorHints: Map<String, String> = emptyMap(),
    val authorized: Boolean = false,
)

data class InstalledAddon(
    val manifestUrl: String,
    val manifest: StremioAddonManifest,
    val enabled: Boolean = true,
    val enabledProfileIds: Set<String> = emptySet(),
    val pinnedCatalogIds: List<String> = emptyList(),
    val hiddenCatalogIds: Set<String> = emptySet(),
    val sortOrder: Int = 0,
)

interface StremioAddonGateway {
    fun loadManifest(manifestUrl: String): StremioAddonManifest
    fun loadCatalog(addon: InstalledAddon, catalog: StremioCatalogDescriptor, extra: Map<String, String> = emptyMap()): List<StremioMetaItem>
    fun loadMeta(addon: InstalledAddon, type: String, id: String): StremioMetaItem?
    fun loadStreams(addon: InstalledAddon, type: String, id: String): List<StremioStreamCandidate>
    fun loadSubtitles(addon: InstalledAddon, type: String, id: String): List<StremioSubtitleCandidate>
}

object StremioEligibility {
    fun playableStreams(candidates: List<StremioStreamCandidate>): List<StremioStreamCandidate> =
        candidates.filter { it.authorized && (!it.url.isNullOrBlank() || !it.externalUrl.isNullOrBlank()) }
}
