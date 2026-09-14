package com.astrawave.app.ui

/** Canonical premium navigation contract for AstraWave phone, tablet and TV surfaces. */
data class AstraWaveNavItem(
    val route: String,
    val label: String,
)

object AstraWaveNavigationContract {
    /** Phone feels like a streaming app: content first, utilities under My. */
    val mobilePrimary = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV"),
        AstraWaveNavItem("live", "Live"),
        AstraWaveNavItem("my", "My"),
    )

    val mobileMore = listOf(
        AstraWaveNavItem("search", "Search"),
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

    /** TV rail follows Nuvio/Viewella-style content priority before utilities. */
    val tv = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV Shows"),
        AstraWaveNavItem("live", "Live TV"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("my", "My AstraWave"),
    )

    init {
        check(mobilePrimary.map { it.route }.distinct().size == mobilePrimary.size)
        check(mobileMore.map { it.route }.distinct().size == mobileMore.size)
        check(mobileTablet.map { it.route }.distinct().size == mobileTablet.size)
        check(tv.map { it.route }.distinct().size == tv.size)
        check(mobilePrimary.map { it.route } == listOf("home", "movies", "tv", "live", "my"))
        check(mobileMore.any { it.route == "search" })
        check(mobileMore.any { it.route == "guide" })
        check(mobileMore.any { it.route == "sports" })
        check(tv.take(4).map { it.route } == listOf("home", "movies", "tv", "live"))
    }
}
