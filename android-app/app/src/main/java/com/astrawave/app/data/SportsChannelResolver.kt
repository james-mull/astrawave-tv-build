package com.astrawave.app.data

import java.util.Locale

/** Sports event metadata normalized before channel/source resolution. */
data class SportsEvent(
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
    val event: SportsEvent,
    val candidates: List<SportsWatchCandidate>,
) {
    val best: SportsWatchCandidate? get() = candidates.firstOrNull()
    val playable: Boolean get() = best != null
}

/**
 * Resolves expected sports broadcasters against the normalized combined channel inventory.
 * Playback still goes through the normal stream health/eligibility path before launch.
 */
class SportsChannelResolver {
    fun resolve(event: SportsEvent, channelGroups: List<LiveChannelGroup>): SportsResolution {
        val broadcasters = event.broadcasterNames.map(::normalize).filter { it.isNotBlank() }
        if (broadcasters.isEmpty()) return SportsResolution(event, emptyList())

        val candidates = buildList {
            channelGroups.forEach { group ->
                group.candidates.forEach { channel ->
                    val channelName = normalize(channel.name)
                    val score = broadcasters.maxOfOrNull { broadcaster -> matchScore(broadcaster, channelName) } ?: 0
                    if (score > 0) {
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
        }.sortedWith(
            compareByDescending<SportsWatchCandidate> { it.broadcasterMatchScore }
                .thenBy { it.priority }
                .thenBy { it.channelName },
        )

        return SportsResolution(event, candidates)
    }

    private fun matchScore(expected: String, channel: String): Int {
        if (expected == channel) return 100
        if (channel.contains(expected) || expected.contains(channel)) return 80
        val expectedTokens = expected.split(' ').filter { it.length > 1 }.toSet()
        val channelTokens = channel.split(' ').filter { it.length > 1 }.toSet()
        if (expectedTokens.isEmpty() || channelTokens.isEmpty()) return 0
        val overlap = expectedTokens.intersect(channelTokens).size
        return when {
            overlap == 0 -> 0
            overlap == expectedTokens.size -> 70
            overlap >= 2 -> 50
            else -> 25
        }
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}
