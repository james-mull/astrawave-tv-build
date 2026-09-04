package com.astrawave.app.ui

import androidx.compose.runtime.Composable

/**
 * Movies entry point for AstraWave's premium visual collection library.
 * The implementation lives in ExpandedListsScreens.kt so Movies and TV can share
 * the same TV-first collection wall, loading, artwork mosaic and detail behavior.
 */
@Composable
fun MovieListsScreen(profileId: String = "default") {
    ExpandedMovieListsScreen()
}
