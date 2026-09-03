package com.astrawave.app.ui

/**
 * Canonical Phase 2 primary-navigation contract.
 *
 * Primary navigation stays intentionally small so AstraWave feels like a consumer streaming
 * product instead of an admin menu. Lower-frequency destinations remain reachable from the
 * owning hubs: Guide from Live TV, Multiview from Live TV/Sports, addon and personal-media
 * management from My AstraWave, and broader catalog exploration from Home/Search.
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
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("my", "My"),
    )

    val tv = listOf(
        AstraWaveNavItem("home", "Home"),
        AstraWaveNavItem("movies", "Movies"),
        AstraWaveNavItem("tv", "TV"),
        AstraWaveNavItem("live", "Live"),
        AstraWaveNavItem("sports", "Sports"),
        AstraWaveNavItem("search", "Search"),
        AstraWaveNavItem("my", "My"),
    )

    init {
        check(mobileTablet.map { it.route }.distinct().size == mobileTablet.size)
        check(tv.map { it.route }.distinct().size == tv.size)
        check(tv.all { tvItem -> mobileTablet.any { it.route == tvItem.route } })
    }
}
