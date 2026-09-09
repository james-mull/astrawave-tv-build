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
        AstraWaveNavItem("movies", "VOD"),
        AstraWaveNavItem("my", "My"),
    )

    /** Secondary destinations live behind My / Content Manager instead of competing with playback. */
    val mobileMore = listOf(
        AstraWaveNavItem("search", "Search Everything"),
        AstraWaveNavItem("guide", "Live Guide"),
        AstraWaveNavItem("tv", "TV Series"),
        AstraWaveNavItem("discover", "Discover"),
        AstraWaveNavItem("multiview", "Multiview"),
        AstraWaveNavItem("audio", "Music & Podcasts"),
        AstraWaveNavItem("personal-media", "Personal Media"),
        AstraWaveNavItem("addons", "Content Sources"),
        AstraWaveNavItem("settings", "Settings"),
    )

    /**
     * Tablet mirrors a dedicated IPTV client: content first, utilities under My.
     * Discover is folded into VOD/Series surfaces and Multiview is launched from Live/Sports.
     */
    val mobileTablet = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("live", "Live TV"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("movies", "VOD"),
        AstraWaveNavItem("tv", "TV Series"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("my", "My AstraWave"),
    )

    /**
     * 10-foot TV rail follows the MYTVOnline-style mental model:
     * Live/Guide/VOD/Series are first-class; source management and power tools stay under My.
     */
    val tv = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("live", "Live TV"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("movies", "VOD"),
        AstraWaveNavItem("tv", "TV Series"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("search", "Search"),
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
        check(mobileTablet.take(4).map { it.route } == listOf("home", "live", "guide", "movies"))
        check(tv.map { it.route } == listOf("home", "live", "guide", "movies", "tv", "sports", "search", "my"))
    }
}
