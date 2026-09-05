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

    /** Premium phone hub destinations exposed behind More, ordered by fastest path to entertainment. */
    val mobileMore = listOf(
        AstraWaveNavItem("search", "Search Everything"),
        AstraWaveNavItem("guide", "Live Guide"),
        AstraWaveNavItem("sports", "Game Day"),
        AstraWaveNavItem("multiview", "Multiview"),
        AstraWaveNavItem("discover", "Discover"),
        AstraWaveNavItem("audio", "Music & Podcasts"),
        AstraWaveNavItem("personal-media", "Personal Media"),
        AstraWaveNavItem("addons", "Source Manager"),
        AstraWaveNavItem("my", "My AstraWave"),
        AstraWaveNavItem("settings", "Settings"),
    )

    /** Full tablet / phone command strip: high-intent actions first, deep catalog browsing second. */
    val mobileTablet = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("live", "Live TV"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Game Day"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV Shows"),
        AstraWaveNavItem("discover", "Discover"),
        AstraWaveNavItem("multiview", "Multiview"),
        AstraWaveNavItem("audio", "Music & Podcasts"),
        AstraWaveNavItem("personal-media", "Personal Media"),
        AstraWaveNavItem("addons", "Source Manager"),
        AstraWaveNavItem("my", "My AstraWave"),
    )

    /** Persistent 10-foot TV rail: immediate watch/search actions first, then browsing and personal surfaces. */
    val tv = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("live", "Live TV"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Game Day"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV Shows"),
        AstraWaveNavItem("discover", "Discover"),
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
        check(mobilePrimary.map { it.label } == listOf("Home", "Movies", "TV", "Live", "More"))
        check(mobileMore.first().route == "search")
        check(mobileMore.take(3).map { it.route } == listOf("search", "guide", "sports"))
        check(mobileMore.any { it.route == "multiview" })
        check(mobileMore.any { it.route == "addons" })
        check(mobileTablet.take(5).map { it.route } == listOf("home", "search", "live", "guide", "sports"))
        check(tv.take(5).map { it.route } == listOf("home", "search", "live", "guide", "sports"))
        check(tv.any { it.route == "multiview" })
        check(tv.any { it.route == "personal-media" })
        check(tv.any { it.route == "addons" })
    }
}
