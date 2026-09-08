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
    fun tabletKeepsHighValueDestinationsVisibleWithoutUtilitySprawl() {
        val routes = AstraWaveNavigationContract.mobileTablet.map { it.route }
        assertEquals(listOf("home", "live", "sports", "movies"), routes.take(4))
        assertTrue("tv" in routes)
        assertTrue("guide" in routes)
        assertTrue("discover" in routes)
        assertTrue("search" in routes)
        assertTrue("my" in routes)
        assertFalse("addons" in routes)
        assertFalse("settings" in routes)
    }

    @Test
    fun tvRailPrioritizesLiveSportsAndGuideAndRetainsPowerFeatures() {
        val routes = AstraWaveNavigationContract.tv.map { it.route }
        assertEquals(listOf("home", "live", "sports", "guide"), routes.take(4))
        listOf("movies", "tv", "discover", "search", "multiview", "audio", "personal-media", "addons", "my").forEach { route ->
            assertTrue(route in routes)
        }
        assertEquals(routes.size, routes.distinct().size)
    }
}
