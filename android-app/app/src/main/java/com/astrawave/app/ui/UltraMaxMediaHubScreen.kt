package com.astrawave.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.MovieListDetailActivity
import com.astrawave.app.TvListDetailActivity
import com.astrawave.app.data.UltraMaxCatalogPreferences
import com.astrawave.app.data.UltraMaxCatalogRegistry

@Composable
fun UltraMaxMediaHubScreen(
    profileId: String,
    media: UltraMaxCatalogRegistry.Media,
) {
    val context = LocalContext.current
    val preferences = remember { UltraMaxCatalogPreferences(context) }
    var revision by remember { mutableIntStateOf(0) }
    val layout = remember(profileId, revision) { preferences.load(profileId) }
    val rows = layout.visibleRows(false).filter { row ->
        when (media) {
            UltraMaxCatalogRegistry.Media.MOVIE -> row.media != UltraMaxCatalogRegistry.Media.SERIES
            UltraMaxCatalogRegistry.Media.SERIES -> row.media != UltraMaxCatalogRegistry.Media.MOVIE
            UltraMaxCatalogRegistry.Media.BOTH -> true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AstraWaveColors.Background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "catalog-hub-header") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (media == UltraMaxCatalogRegistry.Media.MOVIE) "Movies" else "TV Shows",
                    color = AstraWaveColors.PrimaryText,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    "Choose a collection, then browse posters and open the watch-first AstraWave detail experience.",
                    color = AstraWaveColors.SecondaryText,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UltraMaxCatalogRegistry.presets.forEach { preset ->
                        FilterChip(
                            selected = layout.presetId == preset.id,
                            onClick = { preferences.applyPreset(profileId, preset.id); revision++ },
                            label = { Text(preset.title) },
                        )
                    }
                }
                Text(
                    "${rows.size} enabled collections • ${layout.language}",
                    color = AstraWaveColors.Accent,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        val families = rows.groupBy { it.family }
        families.forEach { (family, familyRows) ->
            item(key = "family-${family.name}") {
                Text(
                    familyLabel(family),
                    color = AstraWaveColors.PrimaryText,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(familyRows, key = { it.id }) { row ->
                AstraWaveFocusableCard(
                    Modifier.fillMaxWidth().clickable {
                        if (media == UltraMaxCatalogRegistry.Media.MOVIE) {
                            context.startActivity(
                                Intent(context, MovieListDetailActivity::class.java)
                                    .putExtra(MovieListDetailActivity.EXTRA_TITLE, row.title)
                                    .putExtra(MovieListDetailActivity.EXTRA_REASON, rowSubtitle(row))
                                    .putExtra(MovieListDetailActivity.EXTRA_QUERY, row.query ?: row.title)
                                    .putExtra(MovieListDetailActivity.EXTRA_GENRE, row.genre)
                                    .putExtra(MovieListDetailActivity.EXTRA_PROFILE_ID, profileId),
                            )
                        } else {
                            context.startActivity(
                                Intent(context, TvListDetailActivity::class.java)
                                    .putExtra(TvListDetailActivity.EXTRA_TITLE, row.title)
                                    .putExtra(TvListDetailActivity.EXTRA_REASON, rowSubtitle(row))
                                    .putExtra(TvListDetailActivity.EXTRA_QUERY, row.query ?: row.title)
                                    .putExtra(TvListDetailActivity.EXTRA_GENRE, row.genre)
                                    .putExtra(TvListDetailActivity.EXTRA_PROFILE_ID, profileId),
                            )
                        }
                    },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                            row.badge?.let { Text(it, color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelMedium) }
                        }
                        Text(rowSubtitle(row), color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
                        Text("Browse collection →", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private fun familyLabel(family: UltraMaxCatalogRegistry.Family): String = when (family) {
    UltraMaxCatalogRegistry.Family.TRENDING -> "Trending & Top Picks"
    UltraMaxCatalogRegistry.Family.NEW_LATEST -> "New & Latest"
    UltraMaxCatalogRegistry.Family.STREAMING_SERVICE -> "Streaming Services"
    UltraMaxCatalogRegistry.Family.GENRE -> "Genres"
    UltraMaxCatalogRegistry.Family.CURATED -> "Curated Collections"
    UltraMaxCatalogRegistry.Family.STUDIO -> "Studios & Franchises"
    UltraMaxCatalogRegistry.Family.DECADE -> "Decades"
    UltraMaxCatalogRegistry.Family.KIDS -> "Family & Kids"
    UltraMaxCatalogRegistry.Family.UK -> "UK Collections"
    UltraMaxCatalogRegistry.Family.TRAKT -> "Trakt"
}

private fun rowSubtitle(row: UltraMaxCatalogRegistry.Row): String = when {
    row.requiresTrakt -> "Personalized Trakt collection when Trakt is connected"
    row.genre != null -> "Browse ${row.genre} titles"
    row.query != null -> "Curated from live metadata for ${row.query}"
    else -> "Live AstraWave catalog collection"
}
