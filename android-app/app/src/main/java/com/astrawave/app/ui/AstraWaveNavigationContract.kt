package com.astrawave.app.ui

/** Canonical navigation contract from the AstraWave v1.0 source of truth. */
data class AstraWaveNavItem(
    val route: String,
    val label: String,
)

object AstraWaveNavigationContract {
    /** Phone bottom navigation: Home | Movies | TV | Live | More. */
    val mobilePrimary = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV"),
        AstraWaveNavItem("live", "Live"),
        AstraWaveNavItem("more", "More"),
    )

    /** Destinations exposed from the phone More surface. */
    val mobileMore = listOf(
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("audio", "Music & Podcasts"),
        AstraWaveNavItem("my", "My AstraWave"),
        AstraWaveNavItem("settings", "Settings"),
    )

    /**
     * Full non-TV destination set retained for tablets/wide layouts while the compact
     * phone shell uses mobilePrimary + mobileMore.
     */
    val mobileTablet = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV"),
        AstraWaveNavItem("live", "Live TV"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("audio", "Music & Podcasts"),
        AstraWaveNavItem("discover", "Discover"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("my", "My AstraWave"),
    )

    /** Persistent 10-foot TV rail. */
    val tv = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV Shows"),
        AstraWaveNavItem("live", "Live TV"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("audio", "Music & Podcasts"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("my", "My AstraWave"),
    )

    init {
        check(mobilePrimary.map { it.route }.distinct().size == mobilePrimary.size)
        check(mobileMore.map { it.route }.distinct().size == mobileMore.size)
        check(mobileTablet.map { it.route }.distinct().size == mobileTablet.size)
        check(tv.map { it.route }.distinct().size == tv.size)
        check(mobilePrimary.map { it.label } == listOf("Home", "Movies", "TV", "Live", "More"))
        check(mobileMore.map { it.label } == listOf("Guide", "Sports", "Search", "Music & Podcasts", "My AstraWave", "Settings"))
        check(tv.map { it.label } == listOf("Home", "Movies", "TV Shows", "Live TV", "Guide", "Sports", "Music & Podcasts", "Search", "My AstraWave"))
    }
}
