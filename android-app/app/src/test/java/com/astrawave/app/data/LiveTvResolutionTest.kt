package com.astrawave.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTvResolutionTest {
    private fun channel(name: String, url: String, tvgId: String? = null, source: String = "test") = LiveChannel(
        id = tvgId ?: name,
        name = name,
        normalizedName = LiveTvRepository.normalizeChannelName(name),
        url = url,
        group = "Sports",
        logo = null,
        tvgId = tvgId,
        source = source,
        priority = 10,
    )

    @Test
    fun paramountPlusDoesNotMatchParamountMovieChannel() {
        val group = LiveChannelGroup(
            canonicalName = "name:paramount movie channel",
            displayName = "Paramount Movie Channel",
            candidates = listOf(channel("Paramount Movie Channel", "https://example.com/paramount.m3u8")),
        )
        val event = SportsGuideEvent(
            id = "1",
            league = "Soccer",
            homeTeam = "A",
            awayTeam = "B",
            startTimeEpochMs = 0,
            broadcasterNames = listOf("Paramount+"),
        )

        assertTrue(SportsChannelResolver().resolve(event, listOf(group)).candidates.isEmpty())
    }

    @Test
    fun genericCbsDoesNotMatchCbsSportsHq() {
        val group = LiveChannelGroup(
            canonicalName = "name:cbs sports hq",
            displayName = "CBS Sports HQ",
            candidates = listOf(channel("CBS Sports HQ", "https://example.com/cbs-hq.m3u8")),
        )
        val event = SportsGuideEvent(
            id = "2",
            league = "NFL",
            homeTeam = "A",
            awayTeam = "B",
            startTimeEpochMs = 0,
            broadcasterNames = listOf("CBS"),
        )

        assertTrue(SportsChannelResolver().resolve(event, listOf(group)).candidates.isEmpty())
    }

    @Test
    fun foxSportsOneAliasMatchesFs1() {
        val group = LiveChannelGroup(
            canonicalName = "name:fs1",
            displayName = "FS1",
            candidates = listOf(channel("FS1", "https://example.com/fs1.m3u8")),
        )
        val event = SportsGuideEvent(
            id = "3",
            league = "College Football",
            homeTeam = "A",
            awayTeam = "B",
            startTimeEpochMs = 0,
            broadcasterNames = listOf("FOX Sports 1"),
        )

        assertEquals("FS1", SportsChannelResolver().resolve(event, listOf(group)).best?.channelName)
    }

    @Test
    fun sameNamedChannelsFromDifferentProvidersMergeIntoAlternates() {
        val repo = LiveTvRepository()
        val groups = repo.merge(
            listOf(
                listOf(channel("News HD", "https://one.example/live.m3u8", "provider-one", "one")),
                listOf(channel("News", "https://two.example/live.m3u8", "provider-two", "two")),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().candidates.size)
    }
}
