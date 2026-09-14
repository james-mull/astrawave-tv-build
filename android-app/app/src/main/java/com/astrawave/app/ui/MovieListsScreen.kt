package com.astrawave.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.astrawave.app.data.BuiltInCatalogMediaType
import com.astrawave.app.data.DynamicCollectionRepository
import com.astrawave.app.data.HouseholdProfileStore

/**
 * Movies entry point. Kids profiles retain the restricted discovery surface; adult profiles
 * use AstraWave's 75 built-in movie catalogs with per-profile ordering/visibility.
 */
@Composable
fun MovieListsScreen(profileId: String = "default") {
    val context = LocalContext.current
    val household = remember { HouseholdProfileStore(context) }
    val isKids = household.profiles().firstOrNull { it.id == profileId }?.kidsMode == true
    if (isKids) {
        KidsDiscoveryScreen(profileId = profileId, media = DynamicCollectionRepository.Media.MOVIE)
    } else {
        BuiltInCatalogHubScreen(profileId = profileId, mediaType = BuiltInCatalogMediaType.MOVIE)
    }
}
