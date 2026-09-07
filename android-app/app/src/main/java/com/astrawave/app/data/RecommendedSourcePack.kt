package com.astrawave.app.data

/**
 * AstraWave's zero-configuration source pack.
 *
 * This is intentionally limited to reviewed/public/catalog/subtitle integrations that can be
 * enabled for a fresh install without silently opting the user into unreviewed community stream
 * execution. Community repositories remain discoverable under Advanced Sources.
 */
object RecommendedSourcePack {
    data class Item(
        val id: String,
        val name: String,
        val category: String,
        val note: String,
    )

    val stremioManifestUrls: List<String> = StremioRepositoryRegistry.recommendedOfficialManifestUrls

    val cloudStreamRepositoryIds: Set<String> = CloudStreamRepositoryRegistry.defaults
        .filter { it.enabledByDefault && it.reviewed }
        .mapTo(linkedSetOf()) { it.id }

    val liveSourceIds: Set<String> = linkedSetOf(
        "all-free",
        "astrawave-free",
        "nexus-us",
        "public-tv",
        "free-tv",
        "iptv-org-us",
        "iptv-org-sports",
        "world-verified",
    )

    val audioProviders: List<Item> = listOf(
        Item("radio-browser", "Radio Browser", "audio", "Worldwide internet-radio directory; no account required."),
        Item("podcast-index", "Podcast Index", "audio", "Primary podcast directory when AstraWave backend credentials are configured; Apple fallback remains available."),
        Item("apple-audio-directory", "Apple audio directories", "audio", "Podcast fallback plus legal music discovery/previews; full commercial playback requires an authorized provider."),
    )

    val providerCatalogs: List<Item> = listOf(
        Item("pluto-tv", "Pluto TV", "availability", "Availability/catalog integration."),
        Item("plex", "Plex", "availability", "Availability/catalog plus optional customer-authorized personal server."),
        Item("tubi", "Tubi", "availability", "Availability/catalog integration."),
        Item("sling-freestream", "Sling Freestream", "availability", "Availability/catalog integration."),
        Item("xumo-play", "Xumo Play", "availability", "Availability/catalog integration."),
        Item("samsung-tv-plus", "Samsung TV Plus", "availability", "Availability/catalog integration."),
    )

    val advancedCloudStreamRepositoryIds: Set<String> = CloudStreamRepositoryRegistry.defaults
        .filterNot { it.id in cloudStreamRepositoryIds }
        .mapTo(linkedSetOf()) { it.id }

    val advancedStremioRepositoryIds: Set<String> = StremioRepositoryRegistry.defaults
        .filterNot { it.official && it.autoDiscover }
        .mapTo(linkedSetOf()) { it.id }
}
