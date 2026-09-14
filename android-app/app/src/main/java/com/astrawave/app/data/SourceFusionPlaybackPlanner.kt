package com.astrawave.app.data

import android.content.Context
import java.util.Locale

/**
 * Converts eligible live/source candidates into a learned, predictive failover order.
 * Only candidates already supplied by an authorized/reviewed connector are considered.
 *
 * Historical health ranks candidates; it does not veto playback. Unknown streams are not probed
 * synchronously on the watch path because many IPTV/CDN endpoints reject lightweight probes while
 * still playing correctly in Media3. The player receives the ordered candidates immediately and
 * performs real playback/failover.
 *
 * Sports Channel Cloud is authoritative for sports ordering because it already combines source
 * health, broadcaster matching and trust. Sports inputs therefore keep their incoming order unless
 * AstraWave has meaningful local failure evidence for a candidate.
 */
class SourceFusionPlaybackPlanner(
    context: Context,
    private val profileId: String = "default",
) {
    private val fusion = SourceFusionRepository(context)
    private val preferenceStore = PlaybackPreferenceStore(context)

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
        val appliedPreset: PlaybackPreset = PlaybackPreset.AUTO,
    ) {
        val orderedUrls: List<String> get() = urls
    }

    private data class BrainRank(
        val ranked: SourceFusionRepository.RankedCandidate,
        val predictiveScore: Int,
        val confidence: Int,
        val recentFailures: Int,
    )

    private data class AuthoritativeRank(
        val input: Input,
        val originalIndex: Int,
        val health: com.astrawave.app.core.SourceHealthScore,
        val recentFailures: Int,
        val confidence: Int,
        val demoted: Boolean,
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
        val preferences = preferenceStore.load(profileId)
        val distinctInputs = applyStrictPreset(inputs.distinctBy { it.url }, preferences)
        if (distinctInputs.all { it.sourceKey.startsWith("sports:") }) {
            return authoritativeSportsPlan(distinctInputs, preferences)
        }

        val now = System.currentTimeMillis()
        val ranked = fusion.rank(
            distinctInputs.map { input ->
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
            val preferenceAdjustment = preferenceAdjustment(
                provider = candidate.candidate.provider,
                qualityLabel = candidate.candidate.qualityLabel,
                url = candidate.candidate.url,
                healthLatencyMs = candidate.health.medianLatencyMs,
                preferences = preferences,
            )
            val predictiveScore = (
                candidate.finalScore + trendAdjustment + preferenceAdjustment - stalePenalty - latencyPenalty - preflightPenalty
            ).coerceIn(0, 150)
            val confidence = confidenceFor(samples.size)
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
            appliedPreset = preferences.preset,
        )
    }

    private fun authoritativeSportsPlan(inputs: List<Input>, preferences: PlaybackPreferences): Plan? {
        if (inputs.isEmpty()) return null
        val ranked = inputs.mapIndexed { index, input ->
            val samples = fusion.samples(input.sourceKey)
            val health = fusion.score(input.sourceKey)
            val recentFailures = samples.takeLast(5).count { !it.reachable }
            val demoted = samples.isNotEmpty() && (
                recentFailures >= 2 || (samples.size >= 3 && health.score < MIN_PLAYABLE_SCORE)
            )
            AuthoritativeRank(
                input = input,
                originalIndex = index,
                health = health,
                recentFailures = recentFailures,
                confidence = confidenceFor(samples.size),
                demoted = demoted,
            )
        }.sortedWith(
            compareBy<AuthoritativeRank> { it.demoted }
                .thenByDescending {
                    preferenceAdjustment(
                        provider = it.input.provider,
                        qualityLabel = it.input.qualityLabel,
                        url = it.input.url,
                        healthLatencyMs = it.health.medianLatencyMs,
                        preferences = preferences,
                    )
                }
                .thenBy { it.originalIndex },
        )

        val best = ranked.firstOrNull() ?: return null
        val backup = ranked.getOrNull(1)
        return Plan(
            urls = ranked.map { it.input.url },
            bestProvider = best.input.provider,
            bestHealthScore = best.health.score,
            backupCount = (ranked.size - 1).coerceAtLeast(0),
            predictiveConfidence = best.confidence,
            backupProvider = backup?.input?.provider,
            preemptiveFailoverRecommended = best.demoted || best.recentFailures >= 2,
            appliedPreset = preferences.preset,
        )
    }

    private fun applyStrictPreset(inputs: List<Input>, preferences: PlaybackPreferences): List<Input> {
        val filtered = when (preferences.preset) {
            PlaybackPreset.DEBRID_ONLY -> inputs.filter(::isDebridInput)
            PlaybackPreset.DIRECT_ONLY -> inputs.filterNot(::isDebridInput)
            else -> inputs
        }
        return filtered.ifEmpty { inputs }
    }

    private fun preferenceAdjustment(
        provider: String,
        qualityLabel: String?,
        url: String,
        healthLatencyMs: Long?,
        preferences: PlaybackPreferences,
    ): Int {
        val text = "${provider.lowercase(Locale.US)} ${qualityLabel.orEmpty().lowercase(Locale.US)} ${url.lowercase(Locale.US)}"
        val isDebrid = DEBRID_MARKERS.any(text::contains)
        val is4k = QUALITY_4K_MARKERS.any(text::contains)
        val is1080 = "1080" in text || "fhd" in text
        val isHdr = HDR_MARKERS.any(text::contains)
        val isSurround = SURROUND_MARKERS.any(text::contains)
        var adjustment = 0

        if (preferences.preferDebrid && isDebrid) adjustment += 7
        if (preferences.preferHdr && isHdr) adjustment += 6
        if (preferences.preferSurround && isSurround) adjustment += 5

        adjustment += when (preferences.preset) {
            PlaybackPreset.AUTO -> 0
            PlaybackPreset.BEST_QUALITY -> when {
                is4k -> 22
                is1080 -> 12
                else -> 0
            }
            PlaybackPreset.FASTEST_START -> when {
                healthLatencyMs == null -> 0
                healthLatencyMs <= 600L -> 20
                healthLatencyMs <= 1_200L -> 12
                healthLatencyMs <= 2_000L -> 5
                else -> -8
            }
            PlaybackPreset.DATA_SAVER -> when {
                is4k -> -24
                is1080 -> -8
                "720" in text -> 16
                else -> 6
            }
            PlaybackPreset.HDR_PREFERRED -> if (isHdr) 28 else 0
            PlaybackPreset.SURROUND_PREFERRED -> if (isSurround) 24 else 0
            PlaybackPreset.DEBRID_ONLY -> if (isDebrid) 35 else -60
            PlaybackPreset.DIRECT_ONLY -> if (!isDebrid) 24 else -60
        }
        return adjustment.coerceIn(-60, 40)
    }

    private fun isDebridInput(input: Input): Boolean {
        val text = "${input.provider.lowercase(Locale.US)} ${input.url.lowercase(Locale.US)}"
        return DEBRID_MARKERS.any(text::contains)
    }

    private fun confidenceFor(sampleCount: Int): Int = when {
        sampleCount >= 20 -> 95
        sampleCount >= 10 -> 85
        sampleCount >= 5 -> 72
        sampleCount >= 2 -> 58
        else -> 40
    }

    companion object {
        private val DEBRID_MARKERS = listOf("real-debrid", "realdebrid", "premiumize", "alldebrid", "debrid")
        private val QUALITY_4K_MARKERS = listOf("2160", "4k", "uhd")
        private val HDR_MARKERS = listOf("hdr", "dolby vision", "dolbyvision", " dv ", "hdr10", "hlg")
        private val SURROUND_MARKERS = listOf("atmos", "7.1", "5.1", "dd+", "eac3", "truehd", "dts")
        private const val MIN_PLAYABLE_SCORE = 35
        private const val PREEMPTIVE_FAILOVER_SCORE = 55
        private const val BACKUP_ADVANTAGE_THRESHOLD = 10
        private const val WARM_AFTER_MS = 15 * 60 * 1000L
        private const val STALE_AFTER_MS = 60 * 60 * 1000L
    }
}
