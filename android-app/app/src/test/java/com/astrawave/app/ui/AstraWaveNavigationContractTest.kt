package com.astrawave.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraWaveNavigationContractTest {

    @Test
    fun phonePrimaryNavigationIsContentFirstAndExactlyFiveItems() {
        val routes = AstraWaveNavigationContract.mobilePrimary.map { it.route }
        assertEquals(listOf("home", "live", "guide", "sports", "settings"), routes)
        assertEquals(5, routes.size)
        assertEquals(routes.size, routes.distinct().size)
        assertEquals("Live", AstraWaveNavigationContract.mobilePrimary.first { it.route == "live" }.label)
        assertEquals("Guide", AstraWaveNavigationContract.mobilePrimary.first { it.route == "guide" }.label)
        assertEquals("More", AstraWaveNavigationContract.mobilePrimary.last().label)
    }

    @Test
    fun utilitiesStayOutOfPhonePrimaryNavigation() {
        val primary = AstraWaveNavigationContract.mobilePrimary.map { it.route }.toSet()
        listOf("movies", "tv", "search", "library", "multiview", "audio", "addons").forEach { route ->
            assertFalse(route in primary)
        }
        assertTrue("live" in primary)
        assertTrue("guide" in primary)
        assertTrue("sports" in primary)
        assertTrue("settings" in primary)
    }

    @Test
    fun phoneMoreKeepsEveryCriticalSecondaryDestinationReachable() {
        val more = AstraWaveNavigationContract.mobileMore.map { it.route }.toSet()
        listOf("movies", "tv", "search", "library", "multiview", "audio", "addons", "settings").forEach { route ->
            assertTrue(route in more)
        }
        assertFalse("live" in more)
    }

    @Test
    fun tabletUsesContentFirstOrderWithoutUtilitySprawl() {
        val routes = AstraWaveNavigationContract.mobileTablet.map { it.route }
        assertEquals(listOf("home", "live", "guide", "sports", "movies", "tv", "search", "library", "settings"), routes)
        assertFalse("discover" in routes)
        assertFalse("multiview" in routes)
        assertFalse("audio" in routes)
        assertTrue("library" in routes)
        assertFalse("addons" in routes)
    }

    @Test
    fun tvRailPrioritizesContentAndKeepsSettingsReachable() {
        val routes = AstraWaveNavigationContract.tv.map { it.route }
        assertEquals(listOf("home", "live", "guide", "sports", "movies", "tv", "search", "library", "settings"), routes)
        assertEquals("Movies", AstraWaveNavigationContract.tv.first { it.route == "movies" }.label)
        assertEquals("Shows", AstraWaveNavigationContract.tv.first { it.route == "tv" }.label)
        assertEquals("Settings", AstraWaveNavigationContract.tv.last().label)
        assertEquals(routes.size, routes.distinct().size)
    }
}
