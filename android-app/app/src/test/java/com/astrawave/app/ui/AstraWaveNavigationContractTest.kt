package com.astrawave.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraWaveNavigationContractTest {

    @Test
    fun phonePrimaryNavigationIsWatchFirstAndExactlyFiveItems() {
        val routes = AstraWaveNavigationContract.mobilePrimary.map { it.route }
        assertEquals(listOf("home", "live", "sports", "movies", "my"), routes)
        assertEquals(5, routes.size)
        assertEquals(routes.size, routes.distinct().size)
        assertEquals("VOD", AstraWaveNavigationContract.mobilePrimary.first { it.route == "movies" }.label)
    }

    @Test
    fun advancedDestinationsStayOutOfPhonePrimaryNavigation() {
        val primary = AstraWaveNavigationContract.mobilePrimary.map { it.route }.toSet()
        listOf("guide", "tv", "discover", "multiview", "audio", "personal-media", "addons", "search").forEach { route ->
            assertFalse(route in primary)
        }
    }

    @Test
    fun phoneMoreKeepsEveryCriticalSecondaryDestinationReachable() {
        val more = AstraWaveNavigationContract.mobileMore.map { it.route }.toSet()
        listOf("search", "guide", "tv", "discover", "multiview", "audio", "personal-media", "addons", "settings").forEach { route ->
            assertTrue(route in more)
        }
    }

    @Test
    fun tabletUsesDedicatedTvClientOrderWithoutUtilitySprawl() {
        val routes = AstraWaveNavigationContract.mobileTablet.map { it.route }
        assertEquals(listOf("home", "live", "guide", "movies", "tv", "sports", "search", "my"), routes)
        assertFalse("discover" in routes)
        assertFalse("multiview" in routes)
        assertFalse("audio" in routes)
        assertFalse("personal-media" in routes)
        assertFalse("addons" in routes)
    }

    @Test
    fun tvRailMatchesTvFirstLiveGuideVodSeriesMentalModel() {
        val routes = AstraWaveNavigationContract.tv.map { it.route }
        assertEquals(listOf("home", "live", "guide", "movies", "tv", "sports", "search", "my"), routes)
        assertEquals("VOD", AstraWaveNavigationContract.tv.first { it.route == "movies" }.label)
        assertEquals("TV Series", AstraWaveNavigationContract.tv.first { it.route == "tv" }.label)
        assertEquals(routes.size, routes.distinct().size)
    }
}
