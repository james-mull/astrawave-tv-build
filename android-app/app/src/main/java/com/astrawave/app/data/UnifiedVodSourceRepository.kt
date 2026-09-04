package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.ScrapeRequest
import com.astrawave.app.core.ScrapedLink
import com.astrawave.app.core.StremioEligibility
import com.astrawave.app.core.StremioResource

/** Unified VOD resolver used by title and episode details. */
class UnifiedVodSourceRepository(context: Context) {
    private val appContext = context.applicationContext
    private val publicSources = SourceDiscoveryRepository()
    private val addonStore = StremioAddonStore(appContext)
    private val gateway = StremioHttpGateway { addon, stream ->
        val direct = stream.optString("url")
        addon.manifestUrl.contains("publicdomainmovies", ignoreCase = true) && direct.startsWith("https://")
    }
    private val catalogAggregator = StremioCatalogAggregator(appContext, gateway)

    suspend fun discover(request: ScrapeRequest, profileId: String = "default"): List<ResolvedSource> {
        val publicResults = publicSources.discover(request)
        val exactId = request.externalIds["stremio_id"]
        val exactType = request.externalIds["stremio_type"]
        val stremioResults = if (!exactId.isNullOrBlank() && !exactType.isNullOrBlank()) {
            discoverApprovedStremioById(exactType, exactId, profileId)
        } else {
            discoverApprovedStremio(request, profileId)
        }
        return (publicResults + stremioResults)
            .distinctBy { it.link.url }
            .sortedByDescending { it.score }
    }

    private fun discoverApprovedStremio(request: ScrapeRequest, profileId: String): List<ResolvedSource> {
        val hit = catalogAggregator.search(query = request.title, profileId = profileId, maxResults = 24)
            .firstOrNull { candidate ->
                candidate.item.name.equals(request.title, ignoreCase = true) ||
                    candidate.item.name.contains(request.title, ignoreCase = true)
            } ?: return emptyList()
        return discoverApprovedStremioById(hit.item.type, hit.item.id, profileId)
    }

    private fun discoverApprovedStremioById(type: String, id: String, profileId: String): List<ResolvedSource> {
        val streamAddons = addonStore.load(profileId)
            .filter { it.enabled && StremioResource.STREAM in it.manifest.resources }

        return streamAddons.flatMap { addon ->
            runCatching { gateway.loadStreams(addon, type, id) }
                .getOrDefault(emptyList())
                .let(StremioEligibility::playableStreams)
                .mapNotNull { stream ->
                    val url = stream.url?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                    val health = runCatching { StreamHealthChecker.check(url) }.getOrNull()
                    if (health?.reachable != true) return@mapNotNull null
                    val label = listOfNotNull(stream.title, stream.name).joinToString(" ")
                    val quality = when {
                        label.contains("2160", true) || label.contains("4k", true) -> "4K"
                        label.contains("1080", true) -> "1080p"
                        label.contains("720", true) -> "720p"
                        label.contains("480", true) -> "480p"
                        else -> "Auto"
                    }
                    val qualityScore = when (quality) {
                        "4K" -> 500
                        "1080p" -> 400
                        "720p" -> 300
                        "480p" -> 180
                        else -> 100
                    }
                    val link = ScrapedLink(
                        url = url,
                        sourceName = stream.name.ifBlank { addon.manifest.name },
                        quality = quality,
                        mimeType = health.contentType,
                        direct = true,
                        licenseLabel = "Approved addon source",
                        attribution = addon.manifest.name,
                    )
                    ResolvedSource(
                        link = link,
                        reachable = true,
                        latencyMs = health.latencyMs,
                        contentType = health.contentType,
                        score = 300 + qualityScore + if (health.latencyMs != null && health.latencyMs < 1_000) 40 else 0,
                    )
                }
        }
    }
}
