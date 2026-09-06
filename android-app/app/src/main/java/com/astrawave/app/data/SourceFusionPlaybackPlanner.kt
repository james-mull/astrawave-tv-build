package com.astrawave.app.data

import android.content.Context

/**
 * Converts merged live/source candidates into a learned failover order.
 * Only candidates already supplied by an eligible source are considered.
 */
class SourceFusionPlaybackPlanner(context: Context) {
    private val fusion = SourceFusionRepository(context)

    data class Plan(
        val urls: List<String>,
        val bestProvider: String?,
        val bestHealthScore: Int?,
        val backupCount: Int,
    )

    fun live(group: LiveChannelGroup): Plan {
        val ranked = fusion.rank(
            group.candidates.map { candidate ->
                SourceFusionRepository.Candidate(
                    sourceKey = "live:${candidate.source}:${candidate.normalizedName}",
                    url = candidate.url,
                    provider = candidate.source,
                    priority = candidate.priority,
                )
            },
            probeUnknown = true,
        ).filter { ranked ->
            val latest = fusion.samples(ranked.candidate.sourceKey).lastOrNull()
            latest?.reachable != false && ranked.health.score >= MIN_PLAYABLE_SCORE
        }
        return Plan(
            urls = ranked.map { it.candidate.url }.distinct(),
            bestProvider = ranked.firstOrNull()?.candidate?.provider,
            bestHealthScore = ranked.firstOrNull()?.health?.score,
            backupCount = (ranked.size - 1).coerceAtLeast(0),
        )
    }

    fun urls(urls: List<String>, provider: String = "Source"): Plan {
        val ranked = fusion.rank(
            urls.distinct().mapIndexed { index, url ->
                SourceFusionRepository.Candidate(
                    sourceKey = "direct:${url.hashCode()}",
                    url = url,
                    provider = provider,
                    priority = 20 + index,
                )
            },
            probeUnknown = true,
        ).filter { ranked ->
            fusion.samples(ranked.candidate.sourceKey).lastOrNull()?.reachable != false &&
                ranked.health.score >= MIN_PLAYABLE_SCORE
        }
        return Plan(
            urls = ranked.map { it.candidate.url },
            bestProvider = ranked.firstOrNull()?.candidate?.provider,
            bestHealthScore = ranked.firstOrNull()?.health?.score,
            backupCount = (ranked.size - 1).coerceAtLeast(0),
        )
    }

    companion object {
        private const val MIN_PLAYABLE_SCORE = 35
    }
}
