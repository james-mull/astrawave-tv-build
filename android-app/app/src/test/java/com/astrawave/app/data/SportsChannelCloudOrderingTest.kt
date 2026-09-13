package com.astrawave.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SportsChannelCloudOrderingTest {
    @Test
    fun cloudHealthRankStaysAuthoritativeThroughSportsResolution() {
        val firstMatch = BackendSportsChannelCloudRepository.ChannelMatch(
            id = "espn-main",
            name = "ESPN",
            logoUrl = null,
            group = "Sports",
            matchScore = 82,
            matchReason = "ESPN alias",
            sourceCount = 2,
            healthScore = 94.0,
            candidates = listOf(
                BackendSportsChannelCloudRepository.Candidate(
                    provider = "Reviewed Public A",
                    sourceId = "reviewed-a",
                    streamUrl = "https://example.com/espn-best.m3u8",
                    healthScore = 96.0,
                ),
                BackendSportsChannelCloudRepository.Candidate(
                    provider = "Reviewed Public B",
                    sourceId = "reviewed-b",
                    streamUrl = "https://example.com/espn-backup.m3u8",
                    healthScore = 89.0,
                ),
            ),
        )
        val laterMatch = BackendSportsChannelCloudRepository.ChannelMatch(
            id = "espn-alt",
            name = "ESPN Alternate",
            logoUrl = null,
            group = "Sports",
            matchScore = 100,
            matchReason = "Exact broadcaster text",
            sourceCount = 1,
            healthScore = 71.0,
            candidates = listOf(
                BackendSportsChannelCloudRepository.Candidate(
                    provider = "Customer Authorized",
                    sourceId = "customer",
                    streamUrl = "https://customer.example/espn-alt.m3u8",
                    healthScore = 71.0,
                ),
            ),
        )
        val cloud = BackendSportsChannelCloudRepository.CloudEvent(
            id = "event-1",
            league = "NFL",
            sport = "football",
            title = "Away at Home",
            startTime = "2026-09-13T18:00:00Z",
            status = "scheduled",
            homeTeam = "Home",
            awayTeam = "Away",
            broadcasts = listOf("ESPN"),
            source = "AstraWave Sports Channel Cloud",
            channelMatches = listOf(firstMatch, laterMatch),
            watchable = true,
            candidateCount = 3,
        )

        val resolution = cloudSportsResolution(cloud)

        assertEquals(listOf("ESPN"), resolution.event.broadcasterNames)
        assertEquals(
            listOf(
                "https://example.com/espn-best.m3u8",
                "https://example.com/espn-backup.m3u8",
                "https://customer.example/espn-alt.m3u8",
            ),
            resolution.candidates.map { it.streamUrl },
        )
        assertEquals("https://example.com/espn-best.m3u8", resolution.best?.streamUrl)
    }
}