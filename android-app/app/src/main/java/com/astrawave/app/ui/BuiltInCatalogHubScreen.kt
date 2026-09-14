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

    val visible = remember(profileId, mediaType, revision) {
        prefs.visible(mediaType, profileId).map(CatalogServiceOverrides::apply)
    }
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
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item("header") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AstraWavePageHeader(
                    title = if (mediaType == BuiltInCatalogMediaType.MOVIE) "Movies" else "TV Shows",
                    subtitle = if (mediaType == BuiltInCatalogMediaType.MOVIE)
                        "Browse movies like a premium streaming app • $mappedCount source-backed catalogs"
                    else "Browse series like a premium streaming app • $mappedCount source-backed catalogs",
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
                    FilterChip(
                        selected = manageMode,
                        onClick = { manageMode = !manageMode },
                        label = { Text(if (manageMode) "Done" else "Manage") },
                    )
                }
            }
        }

        if (!manageMode) {
            catalogSections(rows, mediaType).forEach { (section, definitions) ->
                item("section-$section") {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(section, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
                        Text(sectionSubtitle(section, mediaType), color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(definitions, key = { "rail-${it.id}" }) { definition ->
                    CatalogContentShelf(definition = definition, profileId = profileId)
                }
            }
        } else {
            item("manage-heading") {
                Text("Manage catalog rows", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
            }
            items(rows, key = { it.id }) { definition ->
                AstraWaveFocusableCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(definition.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                                Text(sourceSubtitle(definition), color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Open", color = AstraWaveColors.AccentStrong, modifier = Modifier.clickable { open(definition) })
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = prefs.isPinned(profileId, definition.id),
                                onClick = { prefs.setPinned(profileId, definition.id, !prefs.isPinned(profileId, definition.id)); revision++ },
                                label = { Text("Pin Home") },
                            )
                            FilterChip(selected = false, onClick = { prefs.move(profileId, mediaType, definition.id, -1); revision++ }, label = { Text("Move up") })
                            FilterChip(selected = false, onClick = { prefs.move(profileId, mediaType, definition.id, 1); revision++ }, label = { Text("Move down") })
                            FilterChip(selected = false, onClick = { prefs.setHidden(profileId, definition.id, true); revision++ }, label = { Text("Hide") })
                        }
                    }
                }
            }

            val hidden = allDefinitions.filter { prefs.isHidden(profileId, it.id) }
            if (hidden.isNotEmpty()) {
                item("hidden-title") { Text("Hidden catalogs", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge) }
                items(hidden, key = { "hidden-${it.id}" }) { definition ->
                    Row(
                        Modifier.fillMaxWidth().background(AstraWaveColors.Surface, MaterialTheme.shapes.medium).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(definition.title, color = AstraWaveColors.SecondaryText)
                        Text("Restore", color = AstraWaveColors.AccentStrong, modifier = Modifier.clickable {
                            prefs.setHidden(profileId, definition.id, false); revision++
                        })
                    }
                }
            }
        }
    }
}

private fun catalogSections(
    rows: List<BuiltInCatalogDefinition>,
    mediaType: BuiltInCatalogMediaType,
): List<Pair<String, List<BuiltInCatalogDefinition>>> {
    val order = if (mediaType == BuiltInCatalogMediaType.MOVIE) {
        listOf("Streaming Services", "Trending & Box Office", "New Releases & Awards", "Movie Genres", "Seasonal & Occasion", "Premium Cinema & World")
    } else {
        listOf("Streaming Services", "Trending & Popular", "New Episodes & Prestige", "TV Genres", "Reality, Docs & Lifestyle", "Kids, Anime & International")
    }
    return order.mapNotNull { section ->
        rows.filter { sectionFor(it, mediaType) == section }.takeIf { it.isNotEmpty() }?.let { section to it }
    }
}

private fun sectionFor(definition: BuiltInCatalogDefinition, mediaType: BuiltInCatalogMediaType): String {
    if (definition.id in CatalogServiceOverrides) return "Streaming Services"
    return if (mediaType == BuiltInCatalogMediaType.MOVIE) {
        when (definition.category) {
            BuiltInCatalogCategory.DISCOVERY -> "Trending & Box Office"
            BuiltInCatalogCategory.EDITORIAL -> "New Releases & Awards"
            BuiltInCatalogCategory.GENRE_THEME -> "Movie Genres"
            BuiltInCatalogCategory.MOOD_SEASONAL -> "Seasonal & Occasion"
            BuiltInCatalogCategory.PREMIUM_INTERNATIONAL -> "Premium Cinema & World"
        }
    } else {
        when (definition.category) {
            BuiltInCatalogCategory.DISCOVERY -> "Trending & Popular"
            BuiltInCatalogCategory.EDITORIAL -> "New Episodes & Prestige"
            BuiltInCatalogCategory.GENRE_THEME -> "TV Genres"
            BuiltInCatalogCategory.MOOD_SEASONAL -> "Reality, Docs & Lifestyle"
            BuiltInCatalogCategory.PREMIUM_INTERNATIONAL -> "Kids, Anime & International"
        }
    }
}

private fun sectionSubtitle(section: String, mediaType: BuiltInCatalogMediaType): String = when (section) {
    "Streaming Services" -> "Netflix, Prime Video, Disney+, Max, Apple TV+, Hulu, Peacock and Paramount+."
    "Trending & Box Office" -> "Popular, watched, anticipated and theatrical movie discovery."
    "New Releases & Awards" -> "Fresh movies, critics' picks, festivals and award-winning cinema."
    "Movie Genres" -> "Action, comedy, drama, horror, sci-fi, documentary and more."
    "Seasonal & Occasion" -> "Holiday, theme, runtime and occasion-based discovery."
    "Premium Cinema & World" -> "4K/HDR, Dolby, anime and international cinema."
    "Trending & Popular" -> "The shows audiences are watching and rating now."
    "New Episodes & Prestige" -> "New series, fresh episodes, limited series and prestige TV."
    "TV Genres" -> "Crime, comedy, drama, mystery, sci-fi, fantasy, horror and more."
    "Reality, Docs & Lifestyle" -> "Reality, true crime, sports, food, travel, nature and documentary TV."
    "Kids, Anime & International" -> "Kids TV, anime, K-dramas, British TV and series from around the world."
    else -> if (mediaType == BuiltInCatalogMediaType.MOVIE) "Movie discovery" else "TV discovery"
}

private fun sourceSubtitle(definition: BuiltInCatalogDefinition): String = when {
    definition.id in CatalogServiceOverrides -> "Streaming service • verified catalog mapping"
    definition.id in VerifiedMdbListCatalogs -> "Verified source-backed catalog"
    else -> "Dynamic AstraWave catalog"
}

private fun categoryLabel(category: BuiltInCatalogCategory): String = when (category) {
    BuiltInCatalogCategory.DISCOVERY -> "Discovery"
    BuiltInCatalogCategory.EDITORIAL -> "Editorial"
    BuiltInCatalogCategory.GENRE_THEME -> "Genres"
    BuiltInCatalogCategory.MOOD_SEASONAL -> "Moods"
    BuiltInCatalogCategory.PREMIUM_INTERNATIONAL -> "World"
}
