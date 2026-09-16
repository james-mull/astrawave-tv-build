package com.astrawave.app.ui

/**
 * Canonical customer navigation contract for AstraWave phone, tablet and TV surfaces.
 * Keep high-frequency entertainment destinations ahead of account, setup and utility screens.
 */
data class AstraWaveNavItem(
    val route: String,
    val label: String,
)

object AstraWaveNavigationContract {
    /** Phone keeps the five highest-frequency streaming destinations one tap away. */
    val mobilePrimary = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("live", "Live"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("settings", "More"),
    )

    /** Profile, library and specialist surfaces stay out of the primary phone bar. */
    val mobileMore = listOf(
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "Shows"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("library", "Library"),
        AstraWaveNavItem("multiview", "Multiview"),
        AstraWaveNavItem("audio", "Music & Podcasts"),
        AstraWaveNavItem("addons", "Content Sources"),
        AstraWaveNavItem("settings", "Settings"),
    )

    /** Tablet mirrors the content-first TV layout. */
    val mobileTablet = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("live", "Live TV"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "Shows"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("library", "Library"),
        AstraWaveNavItem("settings", "Settings"),
    )

    /** TV rail follows content priority before utilities. */
    val tv = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("live", "Live TV"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "Shows"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("library", "Library"),
        AstraWaveNavItem("settings", "Settings"),
    )

    init {
        check(mobilePrimary.map { it.route }.distinct().size == mobilePrimary.size)
        check(mobileMore.map { it.route }.distinct().size == mobileMore.size)
        check(mobileTablet.map { it.route }.distinct().size == mobileTablet.size)
        check(tv.map { it.route }.distinct().size == tv.size)
        check(mobilePrimary.map { it.route } == listOf("home", "live", "guide", "sports", "settings"))
        check(mobileMore.any { it.route == "library" })
        check(tv.take(4).map { it.route } == listOf("home", "live", "guide", "sports"))
        check(tv.last().route == "settings")
    }
}
