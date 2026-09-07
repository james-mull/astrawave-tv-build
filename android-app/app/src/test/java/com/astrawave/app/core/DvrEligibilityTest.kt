package com.astrawave.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DvrEligibilityTest {
    private val capabilities = LiveSourceCapabilities(
        sourceId = "customer-source",
        supportsDvr = true,
        maxRecordingHours = 4,
    )

    @Test
    fun recordingRequiresAuthorizedHttpCandidate() {
        val now = 1_000_000L
        val missing = RecordingRequest(
            id = "rec-1",
            profileId = "default",
            sourceId = "customer-source",
            channelId = "channel",
            title = "Program",
            startEpochMs = now + 1_000L,
            endEpochMs = now + 61_000L,
        )
        assertTrue(DvrEligibility.validateRequest(missing, capabilities, now).any { it.contains("authorized HTTP") })

        val valid = missing.copy(authorizedStreamUrls = listOf("https://provider.example/live/channel.ts"))
        assertFalse(DvrEligibility.validateRequest(valid, capabilities, now).any { it.contains("authorized HTTP") })
    }

    @Test
    fun overlapDetectionRejectsConcurrentSourceWindows() {
        val first = RecordingRequest("a","p","s","c1","One",10_000L,20_000L,authorizedStreamUrls=listOf("https://example.com/a.ts"))
        val second = RecordingRequest("b","p","s","c2","Two",15_000L,25_000L,authorizedStreamUrls=listOf("https://example.com/b.ts"))
        val adjacent = RecordingRequest("c","p","s","c3","Three",20_000L,30_000L,authorizedStreamUrls=listOf("https://example.com/c.ts"))
        assertTrue(DvrEligibility.overlaps(first, second))
        assertFalse(DvrEligibility.overlaps(first, adjacent))
    }
}
