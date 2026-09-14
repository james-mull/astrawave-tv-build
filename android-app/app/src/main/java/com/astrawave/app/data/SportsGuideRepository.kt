package com.astrawave.app.data

import com.astrawave.app.core.IptvSource
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Sports command-center projection. Schedule metadata is kept separate from
 * broadcaster metadata so AstraWave never invents a playable source.
 */
enum class SportsAvailabilityReason {
    READY,
    NO_BROADCAST_METADATA,
    NO_CHANNEL_MATCH,
    NO_HEALTHY_CANDIDATE,
}

data class SportsGuideItem(
    val event: SportsEvent,
    val broadcasterNames: List<String>,
    val resolution: SportsResolution?,
    val addonCatalogMatches: List<String> = emptyList(),
    val watchCandidate: SportsWatchCandidate? = resolution?.best,
    val availabilityReason: SportsAvailabilityReason = when {
        resolution?.best != null -> SportsAvailabilityReason.READY
        broadcasterNames.isEmpty() -> SportsAvailabilityReason.NO_BROADCAST_METADATA
        resolution?.candidates.isNullOrEmpty() -> SportsAvailabilityReason.NO_CHANNEL_MATCH
        else -> SportsAvailabilityReason.NO_HEALTHY_CANDIDATE
    },
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

internal fun cloudSportsResolution(cloud: BackendSportsChannelCloudRepository.CloudEvent): SportsResolution {
    val guideEvent = SportsGuideEvent(
        id = cloud.id,
        league = cloud.league,
        homeTeam = cloud.homeTeam.orEmpty(),
        awayTeam = cloud.awayTeam.orEmpty(),
        startTimeEpochMs = 0L,
        broadcasterNames = cloud.broadcasts,
    )
    val candidates = cloud.channelMatches.flatMapIndexed { matchIndex, match ->
        match.candidates.mapIndexed { candidateIndex, candidate ->
            SportsWatchCandidate(
                eventId = cloud.id,
                channelName = match.name,
                source = candidate.provider,
                streamUrl = candidate.streamUrl,
                broadcasterMatchScore = match.matchScore,
                priority = (matchIndex * 1_000) + candidateIndex,
            )
        }
    }.distinctBy { it.streamUrl }
    return SportsResolution(guideEvent, candidates)
}

class SportsGuideRepository(
    private val sportsDb: TheSportsDbClient = TheSportsDbClient(),
    private val combinedLiveTv: CombinedLiveTvRepository = CombinedLiveTvRepository(),
    private val resolver: SportsChannelResolver = SportsChannelResolver(),
    private val broadcasterProvider: SportsBroadcasterProvider = SportsBroadcasterProvider { emptyList() },
    private val sportsCloud: BackendSportsChannelCloudRepository = BackendSportsChannelCloudRepository(),
) {
    fun load(
        date: LocalDate = LocalDate.now(),
        sources: List<IptvSource> = emptyList(),
        sport: String? = null,
        addonSportsChannelNames: List<String> = emptyList(),
    ): SportsGuideSnapshot {
        // Older UI builds used UTC for "today". During evening hours in North America that can
        // already be tomorrow. Normalize that legacy value back to the device's actual local day.
        val localToday = LocalDate.now()
        val utcToday = LocalDate.now(ZoneOffset.UTC)
        val effectiveDate = if (date == utcToday && utcToday != localToday) localToday else date
        val dateText = effectiveDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

        val cloudEvents = runCatching { sportsCloud.events(effectiveDate, days = 1) }.getOrDefault(emptyList())
            .filter { cloud -> sport.isNullOrBlank() || cloud.sport.equals(sport, ignoreCase = true) }

        val live = combinedLiveTv.load(
            userSourcesConfig = sources,
            includeEpg = false,
            expandedPublicInventory = false,
        )

        if (cloudEvents.isNotEmpty()) {
            return cloudSnapshot(dateText, cloudEvents, addonSportsChannelNames, live)
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
                resolver.resolve(guideEvent, live.groups)
            }

            val addonMatches = if (broadcasters.isEmpty()) emptyList() else addonSportsChannelNames.filter { addonName ->
                broadcasters.any { broadcaster -> channelNamesLikelyMatch(addonName, broadcaster) }
            }.distinct().take(8)

            val availability = when {
                resolution?.best != null -> SportsAvailabilityReason.READY
                broadcasters.isEmpty() -> SportsAvailabilityReason.NO_BROADCAST_METADATA
                resolution == null || resolution.candidates.isEmpty() -> SportsAvailabilityReason.NO_CHANNEL_MATCH
                else -> SportsAvailabilityReason.NO_HEALTHY_CANDIDATE
            }
            SportsGuideItem(event, broadcasters, resolution, addonMatches, availabilityReason = availability)
        }.sortedWith(sportsDisplayOrder())

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
        live: CombinedLiveTvSnapshot,
    ): SportsGuideSnapshot {
        val items = cloudEvents.map { cloud ->
            val localStart = parseCloudTime(cloud.startTime)
            val startDate = localStart?.toLocalDate()?.format(DateTimeFormatter.ISO_LOCAL_DATE)
                ?: cloud.startTime.take(10).takeIf { it.length == 10 }
                ?: dateText
            val startTime = localStart?.format(DateTimeFormatter.ofPattern("h:mm a"))
                ?: cloud.startTime.substringAfter('T', "").take(5).takeIf(String::isNotBlank)

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

            val cloudResolution = cloudSportsResolution(cloud)
            val localResolution = if (cloud.broadcasts.isEmpty()) {
                null
            } else {
                resolver.resolve(cloudResolution.event, live.groups)
            }
            val mergedCandidates = (cloudResolution.candidates + localResolution?.candidates.orEmpty())
                .distinctBy { it.streamUrl }
            val resolution = SportsResolution(cloudResolution.event, mergedCandidates)

            val addonMatches = cloud.broadcasts.flatMap { broadcaster ->
                addonSportsChannelNames.filter { channelNamesLikelyMatch(it, broadcaster) }
            }.distinct().take(8)

            val availability = when {
                resolution.best != null -> SportsAvailabilityReason.READY
                cloud.broadcasts.isEmpty() -> SportsAvailabilityReason.NO_BROADCAST_METADATA
                cloud.unwatchableReason == "NO_HEALTHY_CANDIDATE" && cloud.channelMatches.isNotEmpty() -> SportsAvailabilityReason.NO_HEALTHY_CANDIDATE
                else -> SportsAvailabilityReason.NO_CHANNEL_MATCH
            }
            SportsGuideItem(event, cloud.broadcasts, resolution, addonMatches, availabilityReason = availability)
        }.sortedWith(sportsDisplayOrder())

        val matchedChannelCount = items.flatMap { it.resolution?.candidates.orEmpty() }
            .map { it.channelName.lowercase() }
            .distinct()
            .size

        return SportsGuideSnapshot(
            date = dateText,
            events = items,
            combinedChannelGroups = maxOf(matchedChannelCount, live.totalChannelGroups),
            addonSportsChannelCount = addonSportsChannelNames.distinct().size,
        )
    }

    private fun parseCloudTime(raw: String): ZonedDateTime? {
        if (raw.isBlank()) return null
        return runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()
            ?: runCatching { Instant.parse(raw).atZone(ZoneId.systemDefault()) }.getOrNull()
    }

    private fun sportsDisplayOrder(): Comparator<SportsGuideItem> =
        compareByDescending<SportsGuideItem> { it.event.isLive && it.watchCandidate != null }
            .thenByDescending { it.watchCandidate != null }
            .thenByDescending { it.event.isLive }
            .thenBy { it.event.time.orEmpty() }
            .thenBy { it.event.name }

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
