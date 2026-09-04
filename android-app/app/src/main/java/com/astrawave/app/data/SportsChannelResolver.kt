package com.astrawave.app.data

import java.util.Locale

/** Sports event metadata normalized before channel/source resolution. */
data class SportsGuideEvent(
    val id: String,
    val league: String,
    val homeTeam: String,
    val awayTeam: String,
    val startTimeEpochMs: Long,
    val broadcasterNames: List<String> = emptyList(),
    val venue: String? = null,
)

data class SportsWatchCandidate(
    val eventId: String,
    val channelName: String,
    val source: String,
    val streamUrl: String,
    val broadcasterMatchScore: Int,
    val priority: Int,
)

data class SportsResolution(
    val event: SportsGuideEvent,
    val candidates: List<SportsWatchCandidate>,
) {
    val best: SportsWatchCandidate? get() = candidates.firstOrNull()
    val playable: Boolean get() = best != null
}

/**
 * Resolves expected sports broadcasters against the normalized combined channel inventory.
 * Playback still goes through the normal stream health/eligibility path before launch.
 *
 * Matching is intentionally strict. Team-branded .TV labels and paid/authorized-only
 * network labels stay as search/broadcaster metadata and are never treated as proof that
 * a similarly named public stream carries the event.
 */
class SportsChannelResolver {
    fun resolve(event: SportsGuideEvent, channelGroups: List<LiveChannelGroup>): SportsResolution {
        val broadcasters = event.broadcasterNames
            .filterNot(::unsafeFallbackBroadcaster)
            .flatMap(::aliasesFor)
            .map(::normalize)
            .filter { it.isNotBlank() }
            .distinct()
        if (broadcasters.isEmpty()) return SportsResolution(event, emptyList())

        val candidates = buildList {
            channelGroups.forEach { group ->
                group.candidates.forEach { channel ->
                    val channelName = normalize(channel.name)
                    val score = broadcasters.maxOfOrNull { broadcaster -> matchScore(broadcaster, channelName) } ?: 0
                    if (score >= MIN_ACCEPTED_SCORE) {
                        add(
                            SportsWatchCandidate(
                                eventId = event.id,
                                channelName = channel.name,
                                source = channel.source,
                                streamUrl = channel.url,
                                broadcasterMatchScore = score,
                                priority = channel.priority,
                            )
                        )
                    }
                }
            }
        }
            .distinctBy { "${it.streamUrl}:${normalize(it.channelName)}" }
            .sortedWith(
                compareByDescending<SportsWatchCandidate> { it.broadcasterMatchScore }
                    .thenBy { it.priority }
                    .thenBy { it.channelName },
            )

        return SportsResolution(event, candidates)
    }

    private fun matchScore(expected: String, channel: String): Int {
        if (expected == channel) return 100
        val expectedTokens = expected.split(' ').filter { it.length > 1 }.toSet()
        val channelTokens = channel.split(' ').filter { it.length > 1 }.toSet()
        if (expectedTokens.isEmpty() || channelTokens.isEmpty()) return 0
        if (expectedTokens.size == 1) return 0

        val overlap = expectedTokens.intersect(channelTokens).size
        return when {
            overlap == expectedTokens.size && expectedTokens.size >= 2 -> 88
            overlap >= 3 && overlap == channelTokens.size -> 84
            else -> 0
        }
    }

    private fun unsafeFallbackBroadcaster(value: String): Boolean {
        if (Regex("\\.tv$", RegexOption.IGNORE_CASE).containsMatchIn(value.trim())) return true
        val normalized = normalize(value)
        return normalized in setOf(
            "mlb tv", "nba tv", "nfl network", "nhl network",
            "espn", "espn2", "espnu", "espn plus",
            "fs1", "fs2", "tnt", "tbs", "trutv",
            "cbs sports network", "cbssn", "sec network", "acc network", "big ten network",
            "peacock", "paramount plus", "prime video", "apple tv", "max", "netflix",
        )
    }

    private fun aliasesFor(value: String): List<String> {
        val normalized = normalize(value)
        return when (normalized) {
            "fox sports 1" -> listOf("fox sports 1")
            "fox sports 2" -> listOf("fox sports 2")
            "nbc sports network", "nbcsn" -> listOf("nbc sports network", "nbcsn")
            "mlb network" -> listOf("mlb network")
            else -> listOf(normalized)
        }
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.US)
        .replace("+", " plus ")
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    companion object {
        private const val MIN_ACCEPTED_SCORE = 84
    }
}
