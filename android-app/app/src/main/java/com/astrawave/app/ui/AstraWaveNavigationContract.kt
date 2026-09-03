package com.astrawave.app.ui

/**
 * Canonical Phase 2 primary-navigation contract.
 *
 * Feature destinations such as Multiview, Personal Media, and Addons remain available through
 * their owning hubs/actions, but are intentionally excluded from the top-level shell so the app
 * stays fast to understand on phone, tablet, and 10-foot TV layouts.
 */
data class AstraWaveNavItem(
    val route: String,
    val label: String,
)

object AstraWaveNavigationContract {
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

    val tv = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV"),
        AstraWaveNavItem("live", "Live TV"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("audio", "Music & Podcasts"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("my", "My AstraWave"),
    )

    init {
        check(mobileTablet.map { it.route }.distinct().size == mobileTablet.size)
        check(tv.map { it.route }.distinct().size == tv.size)
        check(tv.all { tvItem -> mobileTablet.any { it.route == tvItem.route } })
    }
}
