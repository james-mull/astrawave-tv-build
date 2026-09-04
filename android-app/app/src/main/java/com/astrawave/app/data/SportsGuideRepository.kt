package com.astrawave.app.data

import com.astrawave.app.core.IptvSource
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Sports command-center projection. Schedule metadata is kept separate from
 * broadcaster metadata so AstraWave never invents a playable source.
 *
 * Broadcaster names are resolved against the combined Live TV lineup, which
 * includes AstraWave Free TV plus the user's enabled M3U/Xtream sources.
 */
data class SportsGuideItem(
    val event: SportsEvent,
    val broadcasterNames: List<String>,
    val resolution: SportsResolution?,
) {
    val watchCandidate: SportsWatchCandidate? get() = resolution?.best
}

data class SportsGuideSnapshot(
    val date: String,
    val events: List<SportsGuideItem>,
    val combinedChannelGroups: Int,
)

fun interface SportsBroadcasterProvider {
    fun broadcastersFor(event: SportsEvent): List<String>
}

class SportsGuideRepository(
    private val sportsDb: TheSportsDbClient = TheSportsDbClient(),
    private val combinedLiveTv: CombinedLiveTvRepository = CombinedLiveTvRepository(),
    private val resolver: SportsChannelResolver = SportsChannelResolver(),
    private val broadcasterProvider: SportsBroadcasterProvider = SportsBroadcasterProvider { emptyList() },
) {
    fun load(
        date: LocalDate = LocalDate.now(ZoneOffset.UTC),
        sources: List<IptvSource> = emptyList(),
        sport: String? = null,
    ): SportsGuideSnapshot {
        val dateText = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val live = combinedLiveTv.load(sources)
        val events = sportsDb.eventsForDay(dateText, sport).map { event ->
            val broadcasters = (
                broadcasterProvider.broadcastersFor(event) +
                    listOfNotNull(event.network)
                )
                .flatMap { value -> value.split('/', ',', ';') }
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()

            val resolution = if (broadcasters.isEmpty()) {
                null
            } else {
                val guideEvent = SportsGuideEvent(
                    id = event.id,
                    league = event.league.orEmpty(),
                    homeTeam = event.homeTeam
                        ?: event.name.substringBefore(" vs ").substringBefore(" v ").trim(),
                    awayTeam = event.awayTeam
                        ?: event.name.substringAfter(" vs ", "").substringAfter(" v ", "").trim(),
                    startTimeEpochMs = 0L,
                    broadcasterNames = broadcasters,
                )
                val matched = resolver.resolve(guideEvent, live.groups)
                matched.copy(
                    candidates = matched.candidates.filter { candidate ->
                        runCatching { StreamHealthChecker.check(candidate.streamUrl).reachable }
                            .getOrDefault(false)
                    },
                )
            }
            SportsGuideItem(event, broadcasters, resolution)
        }
        return SportsGuideSnapshot(
            date = dateText,
            events = events,
            combinedChannelGroups = live.totalChannelGroups,
        )
    }
}
