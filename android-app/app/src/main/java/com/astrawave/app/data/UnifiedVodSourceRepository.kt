package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.ScrapeRequest
import com.astrawave.app.core.ScrapedLink
import com.astrawave.app.core.StremioEligibility
import com.astrawave.app.core.StremioResource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Unified VOD resolver used by movie, series and episode detail surfaces.
 *
 * The repository intentionally separates catalog discovery from playback eligibility. Enabled
 * addons may enrich metadata, but only sources that pass AstraWave's explicit authorization policy
 * and health checks are returned as playable candidates.
 */
class UnifiedVodSourceRepository(context: Context) {
    private val appContext = context.applicationContext
    private val publicSources = SourceDiscoveryRepository()
    private val addonStore = StremioAddonStore(appContext)
    private val authorizationStore = VodProviderAuthorizationStore(appContext)
    private val catalogGateway = StremioHttpGateway()
    private val catalogAggregator = StremioCatalogAggregator(appContext, catalogGateway)
    private val healthCache = linkedMapOf<String, CachedHealth>()

    /** Backwards-compatible source list consumed by the current TitleDetailsActivity. */
    suspend fun discover(request: ScrapeRequest, profileId: String = "default"): List<ResolvedSource> =
        plan(request, profileId).sources

    /**
     * Resolve the title into one ranked playback plan. The first URL is the preferred source and
     * the remaining URLs are ordered backups ready for PlayerActivity's existing failover engine.
     */
    suspend fun plan(request: ScrapeRequest, profileId: String = "default"): VodPlaybackPlan = coroutineScope {
        val publicDeferred = async { runCatching { publicSources.discover(request) }.getOrDefault(emptyList()) }
        val stremioDeferred = async {
            runCatching {
                val exactId = request.externalIds["stremio_id"]
                val exactType = request.externalIds["stremio_type"]
                if (!exactId.isNullOrBlank() && !exactType.isNullOrBlank()) {
                    discoverApprovedStremioById(exactType, exactId, profileId)
                } else {
                    discoverApprovedStremio(request, profileId)
                }
            }.getOrDefault(emptyList())
        }

        val ranked = (publicDeferred.await() + stremioDeferred.await())
            .groupBy { normalizeUrl(it.link.url) }
            .mapNotNull { (_, duplicates) -> duplicates.maxByOrNull { it.score } }
            .sortedWith(
                compareByDescending<ResolvedSource> { it.score }
                    .thenBy { it.latencyMs ?: Long.MAX_VALUE },
            )

        val providers = ranked.map { it.link.sourceName }.filter(String::isNotBlank).distinct()
        val qualities = ranked.mapNotNull { it.link.quality?.takeIf(String::isNotBlank) }.distinct()
        VodPlaybackPlan(
            sources = ranked,
            preferred = ranked.firstOrNull(),
            backups = ranked.drop(1),
            urls = ranked.map { it.link.url }.distinct(),
            providers = providers,
            qualities = qualities,
            bestQuality = ranked.maxByOrNull { qualityWeight(it.link.quality) }?.link?.quality,
        )
    }

    private fun discoverApprovedStremio(request: ScrapeRequest, profileId: String): List<ResolvedSource> {
        val normalizedTitle = normalizeTitle(request.title)
        val hits = catalogAggregator.search(query = request.title, profileId = profileId, maxResults = 36)
            .map { hit ->
                val candidateTitle = normalizeTitle(hit.item.name)
                val titleScore = when {
                    candidateTitle == normalizedTitle -> 100
                    candidateTitle.startsWith(normalizedTitle) || normalizedTitle.startsWith(candidateTitle) -> 70
                    candidateTitle.contains(normalizedTitle) || normalizedTitle.contains(candidateTitle) -> 45
                    else -> 0
                }
                hit to titleScore
            }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }

        val preferred = hits.firstOrNull()?.first ?: return emptyList()
        val streamId = if (request.season != null && request.episode != null) {
            "${preferred.item.id}:${request.season}:${request.episode}"
        } else {
            preferred.item.id
        }
        val streamType = if (request.season != null && request.episode != null) "series" else preferred.item.type
        return discoverApprovedStremioById(streamType, streamId, profileId)
    }

    private fun discoverApprovedStremioById(type: String, id: String, profileId: String): List<ResolvedSource> {
        val streamAddons = addonStore.load(profileId)
            .filter { it.enabled && StremioResource.STREAM in it.manifest.resources }
        val gateway = streamGateway(profileId)

        return streamAddons.flatMap { addon ->
            runCatching { gateway.loadStreams(addon, type, id) }
                .getOrDefault(emptyList())
                .let(StremioEligibility::playableStreams)
                .mapNotNull { stream ->
                    val url = stream.url?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                    val health = healthFor(url)
                    if (health?.reachable != true) return@mapNotNull null
                    val label = listOfNotNull(stream.title, stream.name).joinToString(" ")
                    val quality = inferQuality(label)
                    val qualityScore = qualityWeight(quality)
                    val latencyBonus = when {
                        health.latencyMs < 500 -> 90
                        health.latencyMs < 900 -> 70
                        health.latencyMs < 1_500 -> 45
                        health.latencyMs < 2_500 -> 20
                        else -> 0
                    }
                    val contentBonus = when {
                        health.contentType?.contains("mpegurl", true) == true -> 35
                        health.contentType?.contains("video", true) == true -> 30
                        else -> 0
                    }
                    val publicDomain = isReviewedPublicDomainManifest(addon.manifestUrl)
                    val link = ScrapedLink(
                        url = url,
                        sourceName = stream.name.ifBlank { addon.manifest.name },
                        quality = quality,
                        mimeType = health.contentType,
                        direct = true,
                        licenseLabel = if (publicDomain) "Reviewed public-domain source" else "Customer-authorized provider",
                        attribution = addon.manifest.name,
                    )
                    ResolvedSource(
                        link = link,
                        reachable = true,
                        latencyMs = health.latencyMs,
                        contentType = health.contentType,
                        score = 350 + qualityScore + latencyBonus + contentBonus,
                    )
                }
        }
    }

    private fun streamGateway(profileId: String): StremioHttpGateway = StremioHttpGateway { addon, stream ->
        val direct = stream.optString("url").trim()
        when {
            !direct.startsWith("https://") -> false
            isReviewedPublicDomainManifest(addon.manifestUrl) -> true
            else -> authorizationStore.isAuthorized(profileId, addon.manifestUrl, direct)
        }
    }

    private fun isReviewedPublicDomainManifest(manifestUrl: String): Boolean =
        manifestUrl.contains("publicdomainmovies", ignoreCase = true)

    private fun healthFor(url: String): StreamHealth? {
        val now = System.currentTimeMillis()
        synchronized(healthCache) {
            healthCache[url]?.takeIf { now - it.checkedAtMs <= HEALTH_CACHE_TTL_MS }?.let { return it.health }
        }
        val health = runCatching { StreamHealthChecker.check(url) }.getOrNull() ?: return null
        synchronized(healthCache) {
            healthCache[url] = CachedHealth(now, health)
            while (healthCache.size > MAX_HEALTH_CACHE_ENTRIES) {
                healthCache.entries.firstOrNull()?.key?.let(healthCache::remove) ?: break
            }
        }
        return health
    }

    private fun inferQuality(label: String): String = when {
        label.contains("2160", true) || label.contains("4k", true) || label.contains("uhd", true) -> "4K"
        label.contains("1440", true) -> "1440p"
        label.contains("1080", true) || label.contains("fhd", true) -> "1080p"
        label.contains("720", true) || label.contains("hd", true) -> "720p"
        label.contains("480", true) -> "480p"
        else -> "Auto"
    }

    private fun qualityWeight(quality: String?): Int = when (quality?.lowercase()) {
        "4k", "2160p", "uhd" -> 520
        "1440p" -> 460
        "1080p", "fhd" -> 410
        "720p", "hd" -> 300
        "480p" -> 180
        else -> 100
    }

    private fun normalizeTitle(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun normalizeUrl(value: String): String = value.trim()
        .replace(Regex("#.*$"), "")
        .trimEnd('/')
        .lowercase()

    private data class CachedHealth(
        val checkedAtMs: Long,
        val health: StreamHealth,
    )

    companion object {
        private const val HEALTH_CACHE_TTL_MS = 120_000L
        private const val MAX_HEALTH_CACHE_ENTRIES = 240
    }
}

/** A single resolver result ready to hand to the player. */
data class VodPlaybackPlan(
    val sources: List<ResolvedSource>,
    val preferred: ResolvedSource?,
    val backups: List<ResolvedSource>,
    val urls: List<String>,
    val providers: List<String>,
    val qualities: List<String>,
    val bestQuality: String?,
) {
    val playable: Boolean get() = preferred != null && urls.isNotEmpty()
    val backupCount: Int get() = backups.size
}
