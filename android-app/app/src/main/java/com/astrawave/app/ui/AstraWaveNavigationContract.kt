package com.astrawave.app.ui

/** Canonical premium navigation contract for AstraWave phone, tablet and TV surfaces. */
data class AstraWaveNavItem(
    val route: String,
    val label: String,
)

object AstraWaveNavigationContract {
    /** Phone bottom navigation stays intentionally simple. */
    val mobilePrimary = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV"),
        AstraWaveNavItem("live", "Live"),
        AstraWaveNavItem("more", "More"),
    )

    /** Premium phone hub destinations exposed behind More. */
    val mobileMore = listOf(
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Game Day"),
        AstraWaveNavItem("multiview", "Multiview"),
        AstraWaveNavItem("discover", "Discover"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("audio", "Radio & Podcasts"),
        AstraWaveNavItem("personal-media", "Personal Media"),
        AstraWaveNavItem("addons", "Source Manager"),
        AstraWaveNavItem("my", "My AstraWave"),
        AstraWaveNavItem("settings", "Settings"),
    )

    /** Full tablet / wide-phone destination set, ordered by viewing workflow. */
    val mobileTablet = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("discover", "Discover"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV Shows"),
        AstraWaveNavItem("live", "Live TV"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Game Day"),
        AstraWaveNavItem("multiview", "Multiview"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("audio", "Radio & Podcasts"),
        AstraWaveNavItem("personal-media", "Personal Media"),
        AstraWaveNavItem("addons", "Source Manager"),
        AstraWaveNavItem("my", "My AstraWave"),
    )

    /** Persistent 10-foot TV rail: discovery first, live/sports together, personal surfaces last. */
    val tv = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("discover", "Discover"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV Shows"),
        AstraWaveNavItem("live", "Live TV"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Game Day"),
        AstraWaveNavItem("multiview", "Multiview"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("audio", "Radio & Podcasts"),
        AstraWaveNavItem("personal-media", "Personal Media"),
        AstraWaveNavItem("addons", "Source Manager"),
        AstraWaveNavItem("my", "My AstraWave"),
    )

    init {
        check(mobilePrimary.map { it.route }.distinct().size == mobilePrimary.size)
        check(mobileMore.map { it.route }.distinct().size == mobileMore.size)
        check(mobileTablet.map { it.route }.distinct().size == mobileTablet.size)
        check(tv.map { it.route }.distinct().size == tv.size)
        check(mobilePrimary.map { it.label } == listOf("Home", "Movies", "TV", "Live", "More"))
        check(mobileMore.first().label == "Guide")
        check(mobileMore.any { it.route == "multiview" })
        check(mobileMore.any { it.route == "addons" })
        check(tv.any { it.route == "multiview" })
        check(tv.any { it.route == "personal-media" })
        check(tv.any { it.route == "addons" })
    }
}
