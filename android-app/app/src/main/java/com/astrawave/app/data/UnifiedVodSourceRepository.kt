package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.ScrapeRequest
import com.astrawave.app.core.ScrapedLink
import com.astrawave.app.core.StremioEligibility
import com.astrawave.app.core.StremioResource

/**
 * Unified VOD resolver used by title details.
 *
 * AstraWave first resolves approved/public sources, then searches enabled Stremio
 * catalogs for the title's normalized Stremio id and asks enabled stream-capable
 * addons for candidates. Native playback remains restricted to reviewed direct
 * sources; external/provider availability is not converted into a fake media URL.
 */
class UnifiedVodSourceRepository(context: Context) {
    private val appContext = context.applicationContext
    private val publicSources = SourceDiscoveryRepository()
    private val addonStore = StremioAddonStore(appContext)
    private val gateway = StremioHttpGateway { addon, stream ->
        val direct = stream.optString("url")
        addon.manifestUrl.contains("publicdomainmovies", ignoreCase = true) &&
            direct.startsWith("https://")
    }
    private val catalogAggregator = StremioCatalogAggregator(appContext, gateway)

    suspend fun discover(
        request: ScrapeRequest,
        profileId: String = "default",
    ): List<ResolvedSource> {
        val publicResults = publicSources.discover(request)
        val stremioResults = discoverApprovedStremio(request, profileId)
        return (publicResults + stremioResults)
            .distinctBy { it.link.url }
            .sortedByDescending { it.score }
    }

    private fun discoverApprovedStremio(
        request: ScrapeRequest,
        profileId: String,
    ): List<ResolvedSource> {
        val hit = catalogAggregator.search(
            query = request.title,
            profileId = profileId,
            maxResults = 24,
        ).firstOrNull { candidate ->
            candidate.item.name.equals(request.title, ignoreCase = true) ||
                candidate.item.name.contains(request.title, ignoreCase = true)
        } ?: return emptyList()

        val streamAddons = addonStore.load(profileId)
            .filter { it.enabled && StremioResource.STREAM in it.manifest.resources }

        return streamAddons.flatMap { addon ->
            runCatching { gateway.loadStreams(addon, hit.item.type, hit.item.id) }
                .getOrDefault(emptyList())
                .let(StremioEligibility::playableStreams)
                .mapNotNull { stream ->
                    val url = stream.url?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                    val health = runCatching { StreamHealthChecker.check(url) }.getOrNull()
                    if (health?.reachable != true) return@mapNotNull null
                    val link = ScrapedLink(
                        url = url,
                        sourceName = stream.name.ifBlank { addon.manifest.name },
                        quality = stream.behaviorHints["videoHash"]?.let { "Direct" },
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
                        score = 300 + if (health.latencyMs != null && health.latencyMs < 1_000) 40 else 0,
                    )
                }
        }
    }
}
