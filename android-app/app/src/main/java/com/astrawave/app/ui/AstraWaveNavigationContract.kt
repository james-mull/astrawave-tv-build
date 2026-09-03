package com.astrawave.app.ui

/**
 * Canonical Phase 2 primary-navigation contract.
 *
 * Primary navigation stays focused on destinations users need frequently. Discover is omitted
 * because Home and Search already cover broad exploration. Guide and Music remain first-class
 * until their owning hubs expose equally direct shortcuts on every supported device class.
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
    }
}
