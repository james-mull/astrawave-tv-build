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
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV"),
        AstraWaveNavItem("live", "Live"),
        AstraWaveNavItem("search", "Search"),
    )

    /** Profile, library and specialist surfaces stay out of the primary phone bar. */
    val mobileMore = listOf(
        AstraWaveNavItem("my", "My AstraWave"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("multiview", "Multiview"),
        AstraWaveNavItem("audio", "Music & Podcasts"),
        AstraWaveNavItem("personal-media", "Personal Media"),
        AstraWaveNavItem("addons", "Content Sources"),
        AstraWaveNavItem("settings", "Settings"),
    )

    /** Tablet mirrors the content-first TV layout. */
    val mobileTablet = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV Shows"),
        AstraWaveNavItem("live", "Live TV"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("my", "My AstraWave"),
    )

    /** TV rail follows content priority before utilities. */
    val tv = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV Shows"),
        AstraWaveNavItem("live", "Live TV"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("my", "My Stuff"),
        AstraWaveNavItem("settings", "Settings"),
    )

    init {
        check(mobilePrimary.map { it.route }.distinct().size == mobilePrimary.size)
        check(mobileMore.map { it.route }.distinct().size == mobileMore.size)
        check(mobileTablet.map { it.route }.distinct().size == mobileTablet.size)
        check(tv.map { it.route }.distinct().size == tv.size)
        check(mobilePrimary.map { it.route } == listOf("home", "movies", "tv", "live", "search"))
        check(mobileMore.any { it.route == "my" })
        check(mobileMore.any { it.route == "guide" })
        check(mobileMore.any { it.route == "sports" })
        check(tv.take(4).map { it.route } == listOf("home", "movies", "tv", "live"))
        check(tv.last().route == "settings")
    }
}
