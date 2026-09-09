package com.astrawave.app.data

import android.content.Context

/**
 * Converts eligible live/source candidates into a learned, predictive failover order.
 * Only candidates already supplied by an authorized/reviewed connector are considered.
 *
 * Historical health ranks candidates; it does not veto playback. Unknown streams are not probed
 * synchronously on the watch path because many IPTV/CDN endpoints reject lightweight probes while
 * still playing correctly in Media3. The player receives the ordered candidates immediately and
 * performs real playback/failover.
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
        val predictiveConfidence: Int? = null,
        val backupProvider: String? = null,
        val preemptiveFailoverRecommended: Boolean = false,
    ) {
        val orderedUrls: List<String> get() = urls
    }

    private data class BrainRank(
        val ranked: SourceFusionRepository.RankedCandidate,
        val predictiveScore: Int,
        val confidence: Int,
        val recentFailures: Int,
    )

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
    fun plan(inputs: List<Input>, probeUnknown: Boolean = false): Plan? {
        if (inputs.isEmpty()) return null
        val now = System.currentTimeMillis()
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
        ).map { candidate ->
            val samples = fusion.samples(candidate.candidate.sourceKey)
            val latest = samples.lastOrNull()
            val recent = samples.takeLast(5)
            val recentFailures = recent.count { !it.reachable }
            val recentSuccesses = recent.count { it.reachable }
            val stalePenalty = latest?.checkedAtEpochMs?.let { checkedAt ->
                when {
                    checkedAt <= 0L -> 0
                    now - checkedAt > STALE_AFTER_MS -> 8
                    now - checkedAt > WARM_AFTER_MS -> 3
                    else -> 0
                }
            } ?: 0
            val trendAdjustment = recentSuccesses * 2 - recentFailures * 10
            val latencyPenalty = when {
                candidate.health.medianLatencyMs == null -> 0
                candidate.health.medianLatencyMs > 4_000L -> 12
                candidate.health.medianLatencyMs > 2_000L -> 6
                candidate.health.medianLatencyMs > 1_000L -> 3
                else -> 0
            }
            val preflightPenalty = when {
                latest?.reachable == false -> 35
                candidate.health.score < MIN_PLAYABLE_SCORE -> 20
                else -> 0
            }
            val predictiveScore = (candidate.finalScore + trendAdjustment - stalePenalty - latencyPenalty - preflightPenalty)
                .coerceIn(0, 130)
            val confidence = when {
                samples.size >= 20 -> 95
                samples.size >= 10 -> 85
                samples.size >= 5 -> 72
                samples.size >= 2 -> 58
                else -> 40
            }
            BrainRank(candidate, predictiveScore, confidence, recentFailures)
        }.sortedWith(
            compareByDescending<BrainRank> { it.predictiveScore }
                .thenByDescending { it.ranked.health.score }
                .thenBy { it.ranked.candidate.provider },
        )

        if (ranked.isEmpty()) return null
        val best = ranked.first()
        val backup = ranked.getOrNull(1)
        val preemptiveFailover = best.recentFailures >= 2 ||
            best.predictiveScore < PREEMPTIVE_FAILOVER_SCORE ||
            (backup != null && backup.predictiveScore >= best.predictiveScore + BACKUP_ADVANTAGE_THRESHOLD)

        return Plan(
            urls = ranked.map { it.ranked.candidate.url }.distinct(),
            bestProvider = best.ranked.candidate.provider,
            bestHealthScore = best.ranked.health.score,
            backupCount = (ranked.size - 1).coerceAtLeast(0),
            predictiveConfidence = best.confidence,
            backupProvider = backup?.ranked?.candidate?.provider,
            preemptiveFailoverRecommended = preemptiveFailover,
        )
    }

    companion object {
        private const val MIN_PLAYABLE_SCORE = 35
        private const val PREEMPTIVE_FAILOVER_SCORE = 55
        private const val BACKUP_ADVANTAGE_THRESHOLD = 10
        private const val WARM_AFTER_MS = 15 * 60 * 1000L
        private const val STALE_AFTER_MS = 60 * 60 * 1000L
    }
}
