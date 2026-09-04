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
 * Resolves sports broadcasters against the normalized combined channel inventory.
 * Matching is confidence-scored instead of driven by a fixed blacklist. Decorative labels
 * such as HD, East/West, Live and Feed are ignored, while weak one-word overlaps remain
 * too risky to claim as a playable match. Playback still goes through stream health checks.
 */
class SportsChannelResolver {
    fun resolve(event: SportsGuideEvent, channelGroups: List<LiveChannelGroup>): SportsResolution {
        val broadcasters = event.broadcasterNames
            .flatMap(::aliasesFor)
            .map(::coreName)
            .filter { it.isNotBlank() }
            .distinct()
        if (broadcasters.isEmpty()) return SportsResolution(event, emptyList())

        val candidates = buildList {
            channelGroups.forEach { group ->
                group.candidates.forEach { channel ->
                    val channelName = coreName(channel.name)
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
                            ),
                        )
                    }
                }
            }
        }
            .distinctBy { "${it.streamUrl}:${coreName(it.channelName)}" }
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

        // A single brand token is only safe as an exact match. This blocks collisions such as
        // Reds.TV -> Redseat The First, ESPN -> ESPN Deportes, and FOX -> FOX Weather.
        if (expectedTokens.size == 1) return 0

        val overlap = expectedTokens.intersect(channelTokens).size
        val coverage = overlap.toDouble() / expectedTokens.size
        val reverseCoverage = overlap.toDouble() / channelTokens.size

        return when {
            overlap == expectedTokens.size && channelTokens.size <= expectedTokens.size + 2 -> 88
            overlap >= 2 && coverage >= 0.75 && reverseCoverage >= 0.60 -> 76
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
            "nba tv", "nbatv" -> listOf("nba tv", "nbatv")
            "mlb network" -> listOf("mlb network")
            "nhl network" -> listOf("nhl network")
            else -> listOf(normalized)
        }
    }

    private fun coreName(value: String): String = normalize(value)
        .replace(Regex("\\b(hd|fhd|uhd|4k|live|east|west|eastern|western|feed|channel)\\b"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalize(value: String): String = value
        .lowercase(Locale.US)
        .replace("+", " plus ")
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    companion object {
        private const val MIN_ACCEPTED_SCORE = 76
    }
}
