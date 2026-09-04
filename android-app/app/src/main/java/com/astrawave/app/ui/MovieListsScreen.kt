package com.astrawave.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.astrawave.app.data.DynamicCollectionRepository
import com.astrawave.app.data.HouseholdProfileStore

/**
 * Movies entry point for AstraWave's profile-aware collection library.
 * Kids profiles are routed to the restricted discovery surface automatically.
 */
@Composable
fun MovieListsScreen(profileId: String = "default") {
    val context = LocalContext.current
    val household = remember { HouseholdProfileStore(context) }
    val isKids = household.profiles().firstOrNull { it.id == profileId }?.kidsMode == true
    if (isKids) {
        KidsDiscoveryScreen(profileId = profileId, media = DynamicCollectionRepository.Media.MOVIE)
    } else {
        DynamicMovieListsScreen(profileId = profileId)
    }
}
