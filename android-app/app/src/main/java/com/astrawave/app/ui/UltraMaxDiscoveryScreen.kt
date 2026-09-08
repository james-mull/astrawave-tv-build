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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.PremiumVodDetailActivity
import com.astrawave.app.data.AstraWaveMetadataGateway
import com.astrawave.app.data.DynamicCollectionRepository
import com.astrawave.app.data.HouseholdProfileStore
import com.astrawave.app.data.UltraMaxCatalogPreferences
import com.astrawave.app.data.UltraMaxCatalogRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UltraMaxDiscoveryScreen(profileId: String = "default") {
    val context = LocalContext.current
    val preferences = remember { UltraMaxCatalogPreferences(context) }
    val household = remember { HouseholdProfileStore(context) }
    val kidsMode = household.profiles().firstOrNull { it.id == profileId }?.kidsMode == true
    var revision by remember { mutableIntStateOf(0) }
    val layout = remember(profileId, revision) { preferences.load(profileId) }
    var customize by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AstraWaveColors.Background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "ultra-header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Discover", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
                Text(
                    if (kidsMode) "A family-safe discovery universe for this profile."
                    else "Your configurable entertainment universe — AstraWave intelligence with Ultra MAX-style catalog control.",
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { customize = !customize }) { Text(if (customize) "Done" else "Customize") }
                    OutlinedButton(onClick = { preferences.reset(profileId); revision++ }) { Text("Reset") }
                }
            }
        }

        if (customize) {
            item(key = "ultra-presets") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quick Presets", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UltraMaxCatalogRegistry.presets
                            .filter { !kidsMode || it.id == "family" }
                            .forEach { preset ->
                                FilterChip(
                                    selected = layout.presetId == preset.id,
                                    onClick = { preferences.applyPreset(profileId, preset.id); revision++ },
                                    label = { Text(preset.title) },
                                )
                            }
                    }
                }
            }
            item(key = "ultra-language") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Catalog Language", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UltraMaxCatalogRegistry.languages.forEach { language ->
                            FilterChip(
                                selected = layout.language == language,
                                onClick = { preferences.setLanguage(profileId, language); revision++ },
                                label = { Text(language) },
                            )
                        }
                    }
                }
            }
            item(key = "ultra-layout") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Home Row Layout", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
                    Text("Show, hide and reorder discovery rows per profile.", color = AstraWaveColors.SecondaryText)
                    layout.orderedRowIds.mapNotNull(UltraMaxCatalogRegistry::row)
                        .filter { !kidsMode || it.kidsSafe }
                        .forEach { row ->
                            val visible = row.id !in layout.hiddenRowIds
                            Row(
                                Modifier.fillMaxWidth().background(AstraWaveColors.Surface, MaterialTheme.shapes.medium).padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = visible,
                                    onClick = { preferences.setVisible(profileId, row.id, !visible); revision++ },
                                    label = { Text(if (visible) "On" else "Off") },
                                )
                                Text(row.title, color = AstraWaveColors.PrimaryText, modifier = Modifier.weight(1f).padding(top = 10.dp))
                                OutlinedButton(onClick = { preferences.move(profileId, row.id, -1); revision++ }) { Text("↑") }
                                OutlinedButton(onClick = { preferences.move(profileId, row.id, 1); revision++ }) { Text("↓") }
                            }
                        }
                }
            }
        }

        val visibleRows = layout.visibleRows(kidsMode)
        visibleRows.forEach { row ->
            item(key = "ultra-row-${row.id}") {
                UltraCatalogRow(row = row, profileId = profileId)
            }
        }
    }
}

@Composable
private fun UltraCatalogRow(row: UltraMaxCatalogRegistry.Row, profileId: String) {
    val context = LocalContext.current
    val dynamic = remember { DynamicCollectionRepository() }
    val metadata = remember { AstraWaveMetadataGateway() }
    var loading by remember(row.id) { mutableStateOf(true) }
    var items by remember(row.id) { mutableStateOf<List<AstraWaveMetadataGateway.Item>>(emptyList()) }
    var error by remember(row.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(row.id) {
        loading = true
        error = null
        items = runCatching {
            withContext(Dispatchers.IO) {
                when {
                    row.requiresTrakt -> emptyList()
                    row.genre != null -> {
                        val media = if (row.media == UltraMaxCatalogRegistry.Media.SERIES) DynamicCollectionRepository.Media.SERIES else DynamicCollectionRepository.Media.MOVIE
                        dynamic.genre(media, row.genre, pages = 2)
                    }
                    row.id == "trending_series" || row.id == "popular_series" || row.id == "top_rated_series" ->
                        dynamic.top(DynamicCollectionRepository.Media.SERIES, pages = 2)
                    row.id == "trending_movies" || row.id == "popular_movies" || row.id == "top_rated_movies" ->
                        dynamic.top(DynamicCollectionRepository.Media.MOVIE, pages = 2)
                    !row.query.isNullOrBlank() -> metadata.search(row.query).filter { item ->
                        when (row.media) {
                            UltraMaxCatalogRegistry.Media.MOVIE -> item.type.equals("movie", true) || item.type.isBlank()
                            UltraMaxCatalogRegistry.Media.SERIES -> item.type.equals("series", true) || item.type.equals("tv", true) || item.type.isBlank()
                            UltraMaxCatalogRegistry.Media.BOTH -> true
                        }
                    }
                    else -> emptyList()
                }.distinctBy { "${it.type}:${it.id}" }.take(20)
            }
        }.onFailure { error = it.message ?: "Unable to load this row" }.getOrDefault(emptyList())
        loading = false
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
            row.badge?.let { Text(it, color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelMedium) }
        }
        when {
            row.requiresTrakt -> AstraWaveStatePanel("${row.title} • Trakt", "Connect Trakt to activate this personalized row.")
            loading -> AstraWaveStatePanel("Loading ${row.title}…", "Building this collection.", loading = true)
            error != null -> AstraWaveStatePanel("${row.title} unavailable", error.orEmpty())
            items.isEmpty() -> AstraWaveStatePanel("No titles right now", "This row has no matching items yet.")
            else -> Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items.forEach { item ->
                    AstraWaveFocusableCard(
                        Modifier.width(154.dp).clickable {
                            val mediaType = if (item.type.equals("series", true) || item.type.equals("tv", true)) "SERIES" else "MOVIE"
                            val sourceId = if (item.id.startsWith("tt", true)) {
                                "stremio:cinemeta:${if (mediaType == "SERIES") "series" else "movie"}:${item.id}"
                            } else "tmdb:${item.id}"
                            context.startActivity(
                                Intent(context, PremiumVodDetailActivity::class.java)
                                    .putExtra(PremiumVodDetailActivity.EXTRA_TITLE, item.name)
                                    .putExtra(PremiumVodDetailActivity.EXTRA_MEDIA_TYPE, mediaType)
                                    .putExtra(PremiumVodDetailActivity.EXTRA_SOURCE_ID, sourceId)
                                    .putExtra(PremiumVodDetailActivity.EXTRA_PROFILE_ID, profileId),
                            )
                        },
                    ) {
                        Column {
                            AstraWaveArtwork(item.name, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text(item.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                            item.releaseInfo?.let { Text(it, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}
