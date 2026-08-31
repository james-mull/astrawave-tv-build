package com.astrawave.app.data

import com.astrawave.app.core.AuthorizedScraperRegistry
import com.astrawave.app.core.ScrapeRequest
import com.astrawave.app.core.ScrapedLink

/**
 * One entry point for AstraWave's automatic, rights-aware online link discovery.
 * Native/open providers and an optional AstraWave-owned feed can be registered here.
 */
class SourceDiscoveryRepository(
    authorizedFeedUrl: String? = null,
    authorizedFeedHosts: Set<String> = emptySet(),
) {
    private val registry = AuthorizedScraperRegistry().apply {
        register(InternetArchiveScraper())
        if (!authorizedFeedUrl.isNullOrBlank() && authorizedFeedHosts.isNotEmpty()) {
            register(AstraWaveAuthorizedFeedScraper(authorizedFeedUrl, authorizedFeedHosts))
        }
    }

    suspend fun discover(request: ScrapeRequest): List<ResolvedSource> {
        return registry.searchAll(request)
            .map { link ->
                val health = runCatching { StreamHealthChecker.check(link.url) }.getOrNull()
                ResolvedSource(
                    link = link,
                    reachable = health?.reachable ?: false,
                    latencyMs = health?.latencyMs,
                    contentType = health?.contentType,
                    score = rank(link, health)
                )
            }
            .filter { it.reachable }
            .sortedByDescending { it.score }
    }

    private fun rank(link: ScrapedLink, health: StreamHealth?): Int {
        var score = when (link.quality?.lowercase()) {
            "4k", "2160p" -> 500
            "1080p" -> 400
            "720p" -> 300
            "480p" -> 200
            else -> 100
        }
        if (link.direct) score += 50
        if (!link.licenseLabel.isNullOrBlank()) score += 40
        if (health?.reachable == true) score += 100
        val latency = health?.latencyMs
        if (latency != null) {
            score += when {
                latency < 500 -> 60
                latency < 1_000 -> 40
                latency < 2_000 -> 20
                else -> 0
            }
        }
        return score
    }
}

data class ResolvedSource(
    val link: ScrapedLink,
    val reachable: Boolean,
    val latencyMs: Long?,
    val contentType: String?,
    val score: Int,
)
