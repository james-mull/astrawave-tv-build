package com.astrawave.app.ui

import androidx.compose.runtime.Composable

/** TV Shows entry point for AstraWave's live-refreshing, profile-aware visual collection library. */
@Composable
fun TvListsScreen(profileId: String = "default") {
    DynamicTvListsScreen(profileId = profileId)
}
