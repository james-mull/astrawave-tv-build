package com.astrawave.app.data

import android.content.Context

/**
 * Converts eligible live/source candidates into a learned failover order.
 * Only candidates already supplied by an authorized/reviewed connector are considered.
 */
class SourceFusionPlaybackPlanner(context: Context) {
    private val fusion = SourceFusionRepository(context)

    data class Input(
        val sourceKey: String,
        val url: String,
        val provider: String,
        val priority: Int = 50,
        val qualityLabel: String? = null,
    )

    data class Plan(
        val urls: List<String>,
        val bestProvider: String?,
        val bestHealthScore: Int?,
        val backupCount: Int,
    ) {
        /** Alias used by UI callers that want to emphasize failover ordering. */
        val orderedUrls: List<String> get() = urls
    }

    fun live(group: LiveChannelGroup): Plan = plan(
        group.candidates.map { candidate ->
            Input(
                sourceKey = "live:${candidate.source}:${candidate.normalizedName}",
                url = candidate.url,
                provider = candidate.source,
                priority = candidate.priority,
            )
        },
    ) ?: Plan(emptyList(), null, null, 0)

    fun urls(urls: List<String>, provider: String = "Source"): Plan = plan(
        urls.distinct().mapIndexed { index, url ->
            Input(
                sourceKey = "direct:${url.hashCode()}",
                url = url,
                provider = provider,
                priority = 20 + index,
            )
        },
    ) ?: Plan(emptyList(), null, null, 0)

    /** Generic route for Guide, Sports and provider-specific adapters. */
    fun plan(inputs: List<Input>, probeUnknown: Boolean = true): Plan? {
        if (inputs.isEmpty()) return null
        val ranked = fusion.rank(
            inputs.distinctBy { it.url }.map { input ->
                SourceFusionRepository.Candidate(
                    sourceKey = input.sourceKey,
                    url = input.url,
                    provider = input.provider,
                    priority = input.priority,
                    qualityLabel = input.qualityLabel,
                )
            },
            probeUnknown = probeUnknown,
        ).filter { ranked ->
            val latest = fusion.samples(ranked.candidate.sourceKey).lastOrNull()
            latest?.reachable != false && ranked.health.score >= MIN_PLAYABLE_SCORE
        }
        if (ranked.isEmpty()) return null
        return Plan(
            urls = ranked.map { it.candidate.url }.distinct(),
            bestProvider = ranked.firstOrNull()?.candidate?.provider,
            bestHealthScore = ranked.firstOrNull()?.health?.score,
            backupCount = (ranked.size - 1).coerceAtLeast(0),
        )
    }

    companion object {
        private const val MIN_PLAYABLE_SCORE = 35
    }
}