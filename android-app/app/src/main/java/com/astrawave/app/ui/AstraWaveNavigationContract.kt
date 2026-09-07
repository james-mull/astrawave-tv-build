package com.astrawave.app.ui

/** Canonical premium navigation contract for AstraWave phone, tablet and TV surfaces. */
data class AstraWaveNavItem(
    val route: String,
    val label: String,
)

object AstraWaveNavigationContract {
    /** Phone bottom navigation stays intentionally simple and watch-first. */
    val mobilePrimary = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("live", "Live"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("my", "My"),
    )

    /** Secondary destinations live behind the account / more hub instead of competing with primary playback. */
    val mobileMore = listOf(
        AstraWaveNavItem("search", "Search Everything"),
        AstraWaveNavItem("guide", "Live Guide"),
        AstraWaveNavItem("tv", "TV Shows"),
        AstraWaveNavItem("discover", "Discover"),
        AstraWaveNavItem("multiview", "Multiview"),
        AstraWaveNavItem("audio", "Music & Podcasts"),
        AstraWaveNavItem("personal-media", "Personal Media"),
        AstraWaveNavItem("addons", "Source Manager"),
        AstraWaveNavItem("settings", "Settings"),
    )

    /** Tablet keeps high-value destinations visible but avoids a sprawling utility strip. */
    val mobileTablet = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("live", "Live TV"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV Shows"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("discover", "Discover"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("my", "My AstraWave"),
    )

    /** Persistent 10-foot TV rail: watch-first, then discovery and personal surfaces. */
    val tv = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("live", "Live TV"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV Shows"),
        AstraWaveNavItem("discover", "Discover"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("multiview", "Multiview"),
        AstraWaveNavItem("audio", "Music & Podcasts"),
        AstraWaveNavItem("personal-media", "Personal Media"),
        AstraWaveNavItem("addons", "Source Manager"),
        AstraWaveNavItem("my", "My AstraWave"),
    )

    init {
        check(mobilePrimary.map { it.route }.distinct().size == mobilePrimary.size)
        check(mobileMore.map { it.route }.distinct().size == mobileMore.size)
        check(mobileTablet.map { it.route }.distinct().size == mobileTablet.size)
        check(tv.map { it.route }.distinct().size == tv.size)
        check(mobilePrimary.map { it.route } == listOf("home", "live", "sports", "movies", "my"))
        check(mobileMore.first().route == "search")
        check(mobileMore.any { it.route == "guide" })
        check(mobileMore.any { it.route == "multiview" })
        check(mobileMore.any { it.route == "addons" })
        check(mobileTablet.take(4).map { it.route } == listOf("home", "live", "sports", "movies"))
        check(tv.take(4).map { it.route } == listOf("home", "live", "sports", "guide"))
        check(tv.any { it.route == "multiview" })
        check(tv.any { it.route == "personal-media" })
        check(tv.any { it.route == "addons" })
    }
}
