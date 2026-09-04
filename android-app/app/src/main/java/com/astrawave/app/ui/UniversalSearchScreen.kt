package com.astrawave.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.data.DynamicCollectionRepository
import com.astrawave.app.data.HouseholdProfileStore
import com.astrawave.app.data.KidsContentRatingRepository
import com.astrawave.app.data.KidsModePolicyStore
import com.astrawave.app.data.StremioCatalogAggregator
import com.astrawave.app.data.StremioSearchHit
import com.astrawave.app.data.TmdbItem
import com.astrawave.app.data.ZeroConfigCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Ready(
        val astrWaveItems: List<TmdbItem>,
        val addonItems: List<StremioSearchHit>,
        val metadataError: String? = null,
        val addonError: String? = null,
    ) : SearchState
    data class Error(val message: String) : SearchState
}

@Composable
fun UniversalSearchScreen(profileId: String = "default") {
    val context = LocalContext.current
    val metadataRepository = remember { ZeroConfigCatalogRepository() }
    val addonSearch = remember { StremioCatalogAggregator(context) }
    val household = remember { HouseholdProfileStore(context) }
    val kidsPolicyStore = remember { KidsModePolicyStore(context) }
    val kidsRating = remember { KidsContentRatingRepository() }
    val profile = remember(profileId) { household.profiles().firstOrNull { it.id == profileId } }
    val isKids = profile?.kidsMode == true
    val kidsPolicy = remember(profileId) { kidsPolicyStore.load(profileId) }
    val bedtime = isKids && kidsPolicyStore.bedtimeActive(profileId)
    val searchAllowed = !isKids || (!bedtime && kidsPolicy.allowSearch && !kidsPolicy.approvedOnly)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(24.dp),
    ) {
        AstraWavePageHeader(
            title = if (isKids) "Kids Search" else "Search",
            subtitle = if (isKids) {
                "Kids Search uses built-in metadata only and applies this profile's content-rating policy before showing results."
            } else {
                "Search AstraWave metadata and your enabled compatible addons in one place — no API key setup required."
            },
        )
        Spacer(Modifier.height(18.dp))

        if (isKids && !searchAllowed) {
            AstraWaveStatePanel(
                title = if (bedtime) "Search paused for bedtime" else "Search disabled in Approved Only mode",
                message = if (bedtime) {
                    "Kids viewing and search are paused during the parent-set bedtime window."
                } else {
                    "Browse Kids Movies and Kids TV instead. In Approved Only mode, individual titles must be unlocked by a parent."
                },
            )
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(if (isKids) "Kids movies and shows" else "Movies, shows and addon catalogs") },
        )
        Spacer(Modifier.height(10.dp))
        AstraWavePrimaryButton(
            label = "Search",
            onClick = {
                val trimmed = query.trim()
                if (trimmed.isEmpty()) {
                    state = SearchState.Idle
                } else if (isKids && !kidsPolicyStore.isSearchTermAllowed(profileId, trimmed)) {
                    state = SearchState.Error("That search is not available in Kids Mode.")
                } else {
                    state = SearchState.Loading
                    scope.launch {
                        state = try {
                            withContext(Dispatchers.IO) {
                                val metadataResult = runCatching { metadataRepository.search(trimmed) }
                                val rawItems = metadataResult.getOrDefault(emptyList())
                                val safeItems = if (isKids) {
                                    val movieItems = rawItems.filter { it.mediaType.equals("movie", true) }
                                    val seriesItems = rawItems.filterNot { it.mediaType.equals("movie", true) }
                                    val movieRatings = kidsRating.ratings(
                                        DynamicCollectionRepository.Media.MOVIE,
                                        movieItems.map { it.id.toString() },
                                    )
                                    val seriesRatings = kidsRating.ratings(
                                        DynamicCollectionRepository.Media.SERIES,
                                        seriesItems.map { it.id.toString() },
                                    )
                                    rawItems.filter { item ->
                                        val movie = item.mediaType.equals("movie", true)
                                        val id = item.id.toString()
                                        val rating = if (movie) movieRatings[id]?.rating else seriesRatings[id]?.rating
                                        kidsPolicyStore.ratingAllowed(profileId, rating, id)
                                    }
                                } else rawItems
                                val addonResult = if (isKids) Result.success(emptyList()) else runCatching { addonSearch.search(trimmed, profileId) }
                                SearchState.Ready(
                                    astrWaveItems = safeItems,
                                    addonItems = addonResult.getOrDefault(emptyList()),
                                    metadataError = metadataResult.exceptionOrNull()?.message,
                                    addonError = addonResult.exceptionOrNull()?.message,
                                )
                            }
                        } catch (error: Exception) {
                            SearchState.Error(error.message ?: "Search failed")
                        }
                    }
                }
            },
            enabled = query.isNotBlank(),
        )
        Spacer(Modifier.height(20.dp))

        when (val current = state) {
            SearchState.Idle -> SearchMessage("Start searching", if (isKids) "Search titles allowed by this Kids profile." else "Enter a title or keyword to search AstraWave discovery.")
            SearchState.Loading -> Row(Modifier.fillMaxWidth().background(AstraWaveColors.Surface, MaterialTheme.shapes.medium).padding(18.dp)) {
                CircularProgressIndicator(color = AstraWaveColors.Accent, strokeWidth = 2.dp)
                Spacer(Modifier.padding(6.dp))
                Text(if (isKids) "Searching and checking ratings…" else "Searching all connected discovery sources…", color = AstraWaveColors.SecondaryText)
            }
            is SearchState.Error -> SearchMessage("Search unavailable", current.message)
            is SearchState.Ready -> {
                if (current.astrWaveItems.isEmpty() && current.addonItems.isEmpty()) {
                    SearchMessage("No matches", if (isKids) "No search results passed this Kids profile's rating and approval rules." else "No connected movie, TV or addon metadata matched this search.")
                } else {
                    if (current.astrWaveItems.isNotEmpty()) {
                        AstraWaveSectionHeader(
                            title = "AstraWave",
                            subtitle = if (isKids) "Certification-filtered built-in metadata." else "Built-in metadata from the AstraWave backend with Cinemeta fallback.",
                        )
                        Spacer(Modifier.height(8.dp))
                        current.astrWaveItems.take(40).forEach { item ->
                            AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Column {
                                    Text(item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        item.overview.ifBlank { "Open for details and Watch options." },
                                        color = AstraWaveColors.SecondaryText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 3,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    LibraryActionRow(item = item.toLibraryItemRef(), profileId = profileId)
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }

                    if (!isKids && current.addonItems.isNotEmpty()) {
                        AstraWaveSectionHeader(title = "From Your Addons", subtitle = "Provider-attributed metadata from enabled compatible addons.")
                        Spacer(Modifier.height(8.dp))
                        current.addonItems.take(40).forEach { hit ->
                            AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Column {
                                    Text(hit.item.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(4.dp))
                                    Text("${hit.addonName} • ${hit.catalogName} • ${hit.item.type}", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelMedium)
                                    hit.item.description?.takeIf { it.isNotBlank() }?.let { description ->
                                        Spacer(Modifier.height(4.dp))
                                        Text(description, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    LibraryActionRow(item = hit.item.toLibraryItemRef(hit.addonId), profileId = profileId)
                                }
                            }
                        }
                    }

                    current.metadataError?.let {
                        Spacer(Modifier.height(12.dp))
                        SearchMessage("Metadata partial failure", it)
                    }
                    if (!isKids) current.addonError?.let {
                        Spacer(Modifier.height(12.dp))
                        SearchMessage("Addon search partial failure", it)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(title: String, message: String) {
    AstraWaveStatePanel(title = title, message = message)
}
