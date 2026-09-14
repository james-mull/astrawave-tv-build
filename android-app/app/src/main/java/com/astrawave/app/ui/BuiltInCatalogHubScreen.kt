package com.astrawave.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.MovieListDetailActivity
import com.astrawave.app.TvListDetailActivity
import com.astrawave.app.data.AstraWaveBuiltInCatalogRegistry
import com.astrawave.app.data.BuiltInCatalogCategory
import com.astrawave.app.data.BuiltInCatalogDefinition
import com.astrawave.app.data.BuiltInCatalogMediaType
import com.astrawave.app.data.BuiltInCatalogPreferences
import com.astrawave.app.data.CatalogServiceOverrides
import com.astrawave.app.data.VerifiedMdbListCatalogs

@Composable
fun BuiltInCatalogHubScreen(
    profileId: String,
    mediaType: BuiltInCatalogMediaType,
) {
    val context = LocalContext.current
    val prefs = remember { BuiltInCatalogPreferences(context) }
    var revision by remember { mutableIntStateOf(0) }
    var selectedCategory by remember { mutableStateOf<BuiltInCatalogCategory?>(null) }
    var manageMode by remember { mutableStateOf(false) }

    val allDefinitions = (if (mediaType == BuiltInCatalogMediaType.MOVIE) {
        AstraWaveBuiltInCatalogRegistry.movies
    } else {
        AstraWaveBuiltInCatalogRegistry.shows
    }).map(CatalogServiceOverrides::apply)
    val visible = remember(profileId, mediaType, revision) { prefs.visible(mediaType, profileId) }
    val rows = visible.filter { selectedCategory == null || it.category == selectedCategory }
    val mappedCount = allDefinitions.count { it.id in VerifiedMdbListCatalogs }

    fun open(definition: BuiltInCatalogDefinition) {
        val intent = if (mediaType == BuiltInCatalogMediaType.MOVIE) {
            Intent(context, MovieListDetailActivity::class.java)
                .putExtra(MovieListDetailActivity.EXTRA_CATALOG_ID, definition.id)
                .putExtra(MovieListDetailActivity.EXTRA_TITLE, definition.title)
                .putExtra(MovieListDetailActivity.EXTRA_REASON, sourceSubtitle(definition))
                .putExtra(MovieListDetailActivity.EXTRA_PROFILE_ID, profileId)
        } else {
            Intent(context, TvListDetailActivity::class.java)
                .putExtra(TvListDetailActivity.EXTRA_CATALOG_ID, definition.id)
                .putExtra(TvListDetailActivity.EXTRA_TITLE, definition.title)
                .putExtra(TvListDetailActivity.EXTRA_REASON, sourceSubtitle(definition))
                .putExtra(TvListDetailActivity.EXTRA_PROFILE_ID, profileId)
        }
        context.startActivity(intent)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AstraWaveColors.Background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "builtin-catalog-header") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AstraWavePageHeader(
                    title = if (mediaType == BuiltInCatalogMediaType.MOVIE) "Movies" else "TV Shows",
                    subtitle = "${allDefinitions.size} built-in dynamic catalogs • $mappedCount verified MDBList mappings • metadata fallback everywhere",
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("All") })
                    BuiltInCatalogCategory.entries.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(categoryLabel(category)) },
                        )
                    }
                    FilterChip(selected = manageMode, onClick = { manageMode = !manageMode }, label = { Text(if (manageMode) "Done" else "Manage") })
                }
                Text(
                    "${visible.size} enabled • ${allDefinitions.size - visible.size} hidden",
                    color = AstraWaveColors.Accent,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        items(rows, key = { it.id }) { definition ->
            AstraWaveFocusableCard(
                Modifier.fillMaxWidth().clickable(enabled = !manageMode) { open(definition) },
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(definition.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (definition.id in VerifiedMdbListCatalogs) Text("MDBLIST", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelSmall)
                            if (definition.id in CatalogServiceOverrides) Text("SERVICE", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelSmall)
                            if (definition.featured) Text("FEATURED", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(sourceSubtitle(definition), color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
                    if (manageMode) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = prefs.isPinned(profileId, definition.id),
                                onClick = {
                                    prefs.setPinned(profileId, definition.id, !prefs.isPinned(profileId, definition.id)); revision++
                                },
                                label = { Text("Pin to Home") },
                            )
                            FilterChip(selected = false, onClick = { prefs.move(profileId, mediaType, definition.id, -1); revision++ }, label = { Text("↑") })
                            FilterChip(selected = false, onClick = { prefs.move(profileId, mediaType, definition.id, 1); revision++ }, label = { Text("↓") })
                            FilterChip(selected = false, onClick = { prefs.setHidden(profileId, definition.id, true); revision++ }, label = { Text("Hide") })
                        }
                    } else {
                        Text("Browse collection →", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (manageMode) {
            val hidden = allDefinitions.filter { prefs.isHidden(profileId, it.id) }
            if (hidden.isNotEmpty()) {
                item(key = "hidden-heading") {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Text("Hidden catalogs", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
                    }
                }
                items(hidden, key = { "hidden-${it.id}" }) { definition ->
                    Row(
                        Modifier.fillMaxWidth().background(AstraWaveColors.Surface, MaterialTheme.shapes.medium).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(definition.title, color = AstraWaveColors.SecondaryText)
                        Text(
                            "Restore",
                            color = AstraWaveColors.Accent,
                            modifier = Modifier.clickable { prefs.setHidden(profileId, definition.id, false); revision++ },
                        )
                    }
                }
                item(key = "reset-catalogs") {
                    Text(
                        "Reset catalog layout",
                        color = AstraWaveColors.Warning,
                        modifier = Modifier.clickable { prefs.reset(profileId); revision++ }.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

private fun sourceSubtitle(definition: BuiltInCatalogDefinition): String = when {
    definition.id in CatalogServiceOverrides -> "Streaming-service discovery • verified MDBList mapping • server-resolved with metadata fallback"
    definition.id in VerifiedMdbListCatalogs -> "Verified MDBList mapping • server-resolved when available • cached metadata fallback"
    else -> "Dynamic AstraWave catalog • live metadata fallback"
}

private fun categoryLabel(category: BuiltInCatalogCategory): String = when (category) {
    BuiltInCatalogCategory.DISCOVERY -> "Discovery"
    BuiltInCatalogCategory.EDITORIAL -> "Editorial"
    BuiltInCatalogCategory.GENRE_THEME -> "Genres"
    BuiltInCatalogCategory.MOOD_SEASONAL -> "Moods"
    BuiltInCatalogCategory.PREMIUM_INTERNATIONAL -> "Premium & World"
}
