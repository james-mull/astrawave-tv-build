package com.astrawave.app.ui

import androidx.compose.runtime.Composable

/**
 * Movies entry point for AstraWave's live-refreshing visual collection library.
 * Live Lists updates automatically; All Collections opens the 100+ curated library.
 */
@Composable
fun MovieListsScreen(profileId: String = "default") {
    DynamicMovieListsScreen()
}
