package com.astrawave.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraWaveNavigationContractTest {

    @Test
    fun phonePrimaryNavigationIsContentFirstAndExactlyFiveItems() {
        val routes = AstraWaveNavigationContract.mobilePrimary.map { it.route }
        assertEquals(listOf("home", "movies", "tv", "live", "my"), routes)
        assertEquals(5, routes.size)
        assertEquals(routes.size, routes.distinct().size)
        assertEquals("Movies", AstraWaveNavigationContract.mobilePrimary.first { it.route == "movies" }.label)
        assertEquals("TV", AstraWaveNavigationContract.mobilePrimary.first { it.route == "tv" }.label)
    }

    @Test
    fun utilitiesStayOutOfPhonePrimaryNavigation() {
        val primary = AstraWaveNavigationContract.mobilePrimary.map { it.route }.toSet()
        listOf("guide", "discover", "multiview", "audio", "personal-media", "addons", "sports", "search").forEach { route ->
            assertFalse(route in primary)
        }
        assertTrue("movies" in primary)
        assertTrue("tv" in primary)
        assertTrue("live" in primary)
    }

    @Test
    fun phoneMoreKeepsEveryCriticalSecondaryDestinationReachable() {
        val more = AstraWaveNavigationContract.mobileMore.map { it.route }.toSet()
        listOf("search", "sports", "guide", "multiview", "audio", "personal-media", "addons", "settings").forEach { route ->
            assertTrue(route in more)
        }
    }

    @Test
    fun tabletUsesContentFirstOrderWithoutUtilitySprawl() {
        val routes = AstraWaveNavigationContract.mobileTablet.map { it.route }
        assertEquals(listOf("home", "movies", "tv", "live", "guide", "sports", "search", "my"), routes)
        assertFalse("discover" in routes)
        assertFalse("multiview" in routes)
        assertFalse("audio" in routes)
        assertFalse("personal-media" in routes)
        assertFalse("addons" in routes)
    }

    @Test
    fun tvRailPrioritizesVodAndSeriesBeforeUtilities() {
        val routes = AstraWaveNavigationContract.tv.map { it.route }
        assertEquals(listOf("home", "movies", "tv", "live", "guide", "sports", "search", "my"), routes)
        assertEquals("Movies", AstraWaveNavigationContract.tv.first { it.route == "movies" }.label)
        assertEquals("TV Shows", AstraWaveNavigationContract.tv.first { it.route == "tv" }.label)
        assertEquals(routes.size, routes.distinct().size)
    }
}
