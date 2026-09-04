package com.astrawave.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.astrawave.app.data.DynamicCollectionRepository
import com.astrawave.app.data.HouseholdProfileStore

/** TV Shows entry point; kids profiles receive the restricted Kids Mode discovery surface. */
@Composable
fun TvListsScreen(profileId: String = "default") {
    val context = LocalContext.current
    val household = remember { HouseholdProfileStore(context) }
    val isKids = household.profiles().firstOrNull { it.id == profileId }?.kidsMode == true
    if (isKids) {
        KidsDiscoveryScreen(profileId = profileId, media = DynamicCollectionRepository.Media.SERIES)
    } else {
        DynamicTvListsScreen(profileId = profileId)
    }
}
