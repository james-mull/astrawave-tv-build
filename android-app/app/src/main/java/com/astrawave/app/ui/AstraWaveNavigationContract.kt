package com.astrawave.app.ui

/**
 * Canonical Phase 2 primary-navigation contract.
 *
 * Keep this aligned with docs/ASTRAWAVE_MASTER_REBUILD_PLAN.md. Mobile/tablet includes
 * Discover as a first-class destination; TV keeps the tighter 10-foot rail defined by the plan.
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
        AstraWaveNavItem("live", "Live"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("audio", "Music"),
        AstraWaveNavItem("discover", "Discover"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("my", "My"),
    )

    val tv = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV"),
        AstraWaveNavItem("live", "Live"),
        AstraWaveNavItem("guide", "Guide"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("audio", "Music"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("my", "My"),
    )

    init {
        check(mobileTablet.map { it.route }.distinct().size == mobileTablet.size)
        check(tv.map { it.route }.distinct().size == tv.size)
        check(tv.all { tvItem -> mobileTablet.any { it.route == tvItem.route } })
        check(mobileTablet.any { it.route == "discover" })
    }
}
