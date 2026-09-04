package com.astrawave.app.ui

import androidx.compose.runtime.Composable

/**
 * Movies entry point for AstraWave's live-refreshing, profile-aware collection library.
 * Live Lists includes personalized shelves; All Collections opens the 100+ curated library.
 */
@Composable
fun MovieListsScreen(profileId: String = "default") {
    DynamicMovieListsScreen(profileId = profileId)
}
