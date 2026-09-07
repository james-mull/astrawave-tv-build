package com.astrawave.app.data

import com.astrawave.app.core.IptvSource
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Sports command-center projection. Schedule metadata is kept separate from
 * broadcaster metadata so AstraWave never invents a playable source.
 *
 * AstraWave prefers the server-fed Sports Channel Cloud so TV, phone and web share
 * the same event/channel mapping. If the backend is unavailable, the existing local
 * TheSportsDB + combined Live TV resolver remains the automatic fallback.
 */
data class SportsGuideItem(
    val event: SportsEvent,
    val broadcasterNames: List<String>,
    val resolution: SportsResolution?,
    val addonCatalogMatches: List<String> = emptyList(),
    val watchCandidate: SportsWatchCandidate? = resolution?.best,
)

data class SportsGuideSnapshot(
    val date: String,
    val events: List<SportsGuideItem>,
    val combinedChannelGroups: Int,
    val addonSportsChannelCount: Int = 0,
)

fun interface SportsBroadcasterProvider {
    fun broadcastersFor(event: SportsEvent): List<String>
}

class SportsGuideRepository(
    private val sportsDb: TheSportsDbClient = TheSportsDbClient(),
    private val combinedLiveTv: CombinedLiveTvRepository = CombinedLiveTvRepository(),
    private val resolver: SportsChannelResolver = SportsChannelResolver(),
    private val broadcasterProvider: SportsBroadcasterProvider = SportsBroadcasterProvider { emptyList() },
    private val sportsCloud: BackendSportsChannelCloudRepository = BackendSportsChannelCloudRepository(),
) {
    fun load(
        date: LocalDate = LocalDate.now(ZoneOffset.UTC),
        sources: List<IptvSource> = emptyList(),
        sport: String? = null,
        addonSportsChannelNames: List<String> = emptyList(),
    ): SportsGuideSnapshot {
        val dateText = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val cloudEvents = runCatching { sportsCloud.events(date, days = 1) }.getOrDefault(emptyList())
            .filter { cloud -> sport.isNullOrBlank() || cloud.sport.equals(sport, ignoreCase = true) }
        if (cloudEvents.isNotEmpty()) return cloudSnapshot(dateText, cloudEvents, addonSportsChannelNames)

        val live = combinedLiveTv.load(sources)
        val healthCache = mutableMapOf<String, Boolean>()

        fun reachable(url: String): Boolean = healthCache.getOrPut(url) {
            runCatching { StreamHealthChecker.check(url).reachable }.getOrDefault(false)
        }

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
                matched.copy(candidates = matched.candidates.filter { reachable(it.streamUrl) })
            }

            val addonMatches = if (broadcasters.isEmpty()) emptyList() else addonSportsChannelNames.filter { addonName ->
                broadcasters.any { broadcaster -> channelNamesLikelyMatch(addonName, broadcaster) }
            }.distinct().take(8)

            SportsGuideItem(event, broadcasters, resolution, addonMatches)
        }
        return SportsGuideSnapshot(
            date = dateText,
            events = events,
            combinedChannelGroups = live.totalChannelGroups,
            addonSportsChannelCount = addonSportsChannelNames.distinct().size,
        )
    }

    private fun cloudSnapshot(
        dateText: String,
        cloudEvents: List<BackendSportsChannelCloudRepository.CloudEvent>,
        addonSportsChannelNames: List<String>,
    ): SportsGuideSnapshot {
        val items = cloudEvents.map { cloud ->
            val startDate = cloud.startTime.take(10).takeIf { it.length == 10 } ?: dateText
            val startTime = cloud.startTime.substringAfter('T', "").take(8).takeIf(String::isNotBlank)
            val event = SportsEvent(
                id = cloud.id,
                name = cloud.title,
                league = cloud.league,
                sport = cloud.sport,
                date = startDate,
                time = startTime,
                homeTeam = cloud.homeTeam,
                awayTeam = cloud.awayTeam,
                status = cloud.status,
                network = cloud.broadcasts.joinToString(" • ").takeIf(String::isNotBlank),
            )
            val guideEvent = SportsGuideEvent(
                id = cloud.id,
                league = cloud.league,
                homeTeam = cloud.homeTeam.orEmpty(),
                awayTeam = cloud.awayTeam.orEmpty(),
                startTimeEpochMs = 0L,
                broadcasterNames = cloud.broadcasts,
            )
            val candidates = cloud.channelMatches.flatMap { match ->
                match.candidates.mapIndexed { index, candidate ->
                    SportsWatchCandidate(
                        eventId = cloud.id,
                        channelName = match.name,
                        source = candidate.provider,
                        streamUrl = candidate.streamUrl,
                        broadcasterMatchScore = match.matchScore,
                        priority = index,
                    )
                }
            }
                .distinctBy { "${it.streamUrl}:${it.channelName}" }
                .sortedWith(compareByDescending<SportsWatchCandidate> { it.broadcasterMatchScore }.thenBy { it.priority })
            val resolution = SportsResolution(guideEvent, candidates)
            val addonMatches = cloud.broadcasts.flatMap { broadcaster ->
                addonSportsChannelNames.filter { channelNamesLikelyMatch(it, broadcaster) }
            }.distinct().take(8)
            SportsGuideItem(event, cloud.broadcasts, resolution, addonMatches)
        }
        val channelCount = cloudEvents.flatMap { it.channelMatches }.map { it.id }.distinct().size
        return SportsGuideSnapshot(
            date = dateText,
            events = items,
            combinedChannelGroups = channelCount,
            addonSportsChannelCount = addonSportsChannelNames.distinct().size,
        )
    }

    private fun channelNamesLikelyMatch(left: String, right: String): Boolean {
        fun normalized(value: String) = value.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        val a = normalized(left)
        val b = normalized(right)
        if (a.isBlank() || b.isBlank()) return false
        return a == b || a.contains(b) || b.contains(a)
    }
}
