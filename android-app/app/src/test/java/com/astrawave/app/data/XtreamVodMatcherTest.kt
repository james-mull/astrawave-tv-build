package com.astrawave.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamVodMatcherTest {
    @Test
    fun qualityAndYearNoiseDoNotBreakExactTitleMatching() {
        assertEquals(
            120,
            XtreamVodMatcher.titleScore("Dune: Part Two", "Dune Part Two (2024) 4K UHD"),
        )
    }

    @Test
    fun unrelatedTitleIsRejected() {
        assertEquals(0, XtreamVodMatcher.titleScore("Dune: Part Two", "Dune Messiah"))
    }

    @Test
    fun exactYearWinsAndLargeYearMismatchIsRejected() {
        assertEquals(40, XtreamVodMatcher.yearScore(2024, 2024))
        assertEquals(-1, XtreamVodMatcher.yearScore(2024, 2021))
    }

    @Test
    fun oneYearMetadataDriftIsAllowedButLowPriority() {
        assertEquals(10, XtreamVodMatcher.yearScore(2024, 2023))
        assertEquals(10, XtreamVodMatcher.yearScore(2024, 2025))
    }

    @Test
    fun episodeMustMatchBothSeasonAndEpisodeExactly() {
        assertTrue(XtreamVodMatcher.episodeMatches(2, 7, 2, 7))
        assertFalse(XtreamVodMatcher.episodeMatches(2, 7, 2, 8))
        assertFalse(XtreamVodMatcher.episodeMatches(2, 7, 3, 7))
    }
}
