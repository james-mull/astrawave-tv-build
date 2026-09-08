package com.astrawave.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.astrawave.app.data.DynamicCollectionRepository
import com.astrawave.app.data.HouseholdProfileStore
import com.astrawave.app.data.UltraMaxCatalogRegistry

/**
 * Movies entry point for AstraWave's profile-aware collection universe.
 * Kids profiles keep the restricted discovery surface; adults get the configurable
 * Ultra MAX-inspired native catalog hub backed by AstraWave metadata and watch-first details.
 */
@Composable
fun MovieListsScreen(profileId: String = "default") {
    val context = LocalContext.current
    val household = remember { HouseholdProfileStore(context) }
    val isKids = household.profiles().firstOrNull { it.id == profileId }?.kidsMode == true
    if (isKids) {
        KidsDiscoveryScreen(profileId = profileId, media = DynamicCollectionRepository.Media.MOVIE)
    } else {
        UltraMaxMediaHubScreen(profileId = profileId, media = UltraMaxCatalogRegistry.Media.MOVIE)
    }
}