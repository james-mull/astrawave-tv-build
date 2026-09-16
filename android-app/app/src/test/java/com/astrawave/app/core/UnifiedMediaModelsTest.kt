package com.astrawave.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedMediaModelsTest {
    @Test
    fun duplicateTitlesMergeSourcesAndAuthorizedPlayback() {
        val first = UnifiedMediaItem(
            id = "tt123",
            kind = UnifiedMediaKind.MOVIE,
            title = "Example",
            sourceLabels = setOf("Stremio"),
            playbackCandidates = listOf(UnifiedPlaybackCandidate("addon", "Stremio", "https://one", authorized = true)),
        )
        val second = first.copy(
            sourceLabels = setOf("CloudStream"),
            playbackCandidates = listOf(UnifiedPlaybackCandidate("repo", "CloudStream", "https://two", authorized = false)),
        )

        val merged = UnifiedCatalogMerger.merge(
            listOf(
                UnifiedCatalogRow("popular", "Popular", listOf(first)),
                UnifiedCatalogRow("popular", "Popular", listOf(second)),
            ),
        ).single().items.single()

        assertEquals(setOf("Stremio", "CloudStream"), merged.sourceLabels)
        assertEquals(2, merged.playbackCandidates.size)
        assertEquals(1, merged.playableCandidates.size)
        assertTrue(merged.playableCandidates.single().authorized)
    }
}
