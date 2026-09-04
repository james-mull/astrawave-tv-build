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
 * Matching is deliberately conservative: a generic shared brand token is not enough to
 * claim that a different FAST channel carries an event. For example, Paramount+ must not
 * resolve to Paramount Movie Channel and CBS must not silently resolve to CBS Sports HQ.
 */
class SportsChannelResolver {
    fun resolve(event: SportsGuideEvent, channelGroups: List<LiveChannelGroup>): SportsResolution {
        val broadcasters = event.broadcasterNames
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

        // One-token network names are only safe as exact matches. This prevents brand-family
        // false positives such as CBS -> CBS Sports HQ or FOX -> FOX Weather.
        if (expectedTokens.size == 1) return 0

        if (channel.contains(expected) || expected.contains(channel)) return 90

        val overlap = expectedTokens.intersect(channelTokens).size
        return when {
            overlap == 0 -> 0
            overlap == expectedTokens.size -> 85
            overlap >= 2 && overlap * 2 >= expectedTokens.size -> 70
            else -> 0
        }
    }

    private fun aliasesFor(value: String): List<String> {
        val normalized = normalize(value)
        return when (normalized) {
            "fs1", "fox sports 1" -> listOf("fs1", "fox sports 1")
            "fs2", "fox sports 2" -> listOf("fs2", "fox sports 2")
            "cbs sports network", "cbssn" -> listOf("cbs sports network", "cbssn")
            "nbc sports network", "nbcsn" -> listOf("nbc sports network", "nbcsn")
            "nfl network" -> listOf("nfl network")
            "nba tv", "nbatv" -> listOf("nba tv", "nbatv")
            "mlb network" -> listOf("mlb network")
            "nhl network" -> listOf("nhl network")
            "espn plus" -> listOf("espn plus")
            "paramount plus" -> listOf("paramount plus")
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
        private const val MIN_ACCEPTED_SCORE = 70
    }
}
