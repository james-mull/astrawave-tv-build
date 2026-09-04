package com.astrawave.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.MovieListDetailActivity
import com.astrawave.app.PlayerActivity
import com.astrawave.app.TitleDetailsActivity
import com.astrawave.app.TvListDetailActivity
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.data.ArtworkRegistry
import com.astrawave.app.data.DynamicCollectionRepository
import com.astrawave.app.data.HouseholdProfileStore
import com.astrawave.app.data.KidsContentRatingRepository
import com.astrawave.app.data.KidsModePolicyStore
import com.astrawave.app.data.StremioCatalogAggregator
import com.astrawave.app.data.StremioSearchHit
import com.astrawave.app.data.UnifiedSearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class SearchFilter(val label: String) {
    ALL("All"), MOVIES("Movies"), TV("TV"), LIVE("Live TV"), SPORTS("Sports"), AUDIO("Audio"), PERSONAL("Personal"), LISTS("Lists"), ADDONS("Addons")
}

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Ready(
        val results: List<UnifiedSearchRepository.Result>,
        val addonItems: List<StremioSearchHit>,
        val partialError: String? = null,
    ) : SearchState
    data class Error(val message: String) : SearchState
}

@Composable
fun UniversalSearchScreen(profileId: String = "default") {
    val context = LocalContext.current
    val unified = remember { UnifiedSearchRepository(context) }
    val addonSearch = remember { StremioCatalogAggregator(context) }
    val household = remember { HouseholdProfileStore(context) }
    val kidsPolicyStore = remember { KidsModePolicyStore(context) }
    val kidsRating = remember { KidsContentRatingRepository() }
    val profile = remember(profileId) { household.profiles().firstOrNull { it.id == profileId } }
    val isKids = profile?.kidsMode == true
    val kidsPolicy = remember(profileId) { kidsPolicyStore.load(profileId) }
    val bedtime = isKids && kidsPolicyStore.bedtimeActive(profileId)
    val searchAllowed = !isKids || (!bedtime && kidsPolicy.allowSearch && !kidsPolicy.approvedOnly)

    var query by remember(profileId) { mutableStateOf("") }
    var selectedFilter by remember(profileId) { mutableStateOf(SearchFilter.ALL) }
    var state by remember(profileId) { mutableStateOf<SearchState>(SearchState.Idle) }
    var searchNonce by remember { mutableIntStateOf(0) }
    var recentsVersion by remember { mutableIntStateOf(0) }

    val recents = remember(profileId, recentsVersion) { unified.recentSearches(profileId) }
    val suggestions = remember(query, profileId, recentsVersion) { unified.suggestions(query, profileId) }
    val filters = remember(isKids) {
        if (isKids) listOf(SearchFilter.ALL, SearchFilter.MOVIES, SearchFilter.TV)
        else SearchFilter.entries
    }

    LaunchedEffect(query, searchNonce, profileId, isKids) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            state = SearchState.Idle
            return@LaunchedEffect
        }
        if (isKids && !kidsPolicyStore.isSearchTermAllowed(profileId, trimmed)) {
            state = SearchState.Error("That search is not available in Kids Mode.")
            return@LaunchedEffect
        }
        delay(if (searchNonce == 0) 420 else 120)
        state = SearchState.Loading
        state = try {
            withContext(Dispatchers.IO) {
                val unifiedResult = runCatching { unified.search(trimmed, profileId, restrictedToKids = isKids) }
                var results = unifiedResult.getOrDefault(emptyList())

                if (isKids) {
                    results = results.filter { it.kind == UnifiedSearchRepository.Kind.MOVIE || it.kind == UnifiedSearchRepository.Kind.TV }
                    val movie = results.filter { it.kind == UnifiedSearchRepository.Kind.MOVIE }
                    val series = results.filter { it.kind == UnifiedSearchRepository.Kind.TV }
                    fun providerId(result: UnifiedSearchRepository.Result): String = when {
                        result.sourceId?.startsWith("stremio:", true) == true -> result.sourceId.substringAfterLast(':')
                        result.sourceId?.startsWith("tmdb:", true) == true -> result.sourceId.substringAfterLast(':')
                        result.id.startsWith("library:") -> result.id.removePrefix("library:")
                        else -> result.id
                    }
                    val movieRatings = kidsRating.ratings(DynamicCollectionRepository.Media.MOVIE, movie.map(::providerId))
                    val seriesRatings = kidsRating.ratings(DynamicCollectionRepository.Media.SERIES, series.map(::providerId))
                    results = results.filter { result ->
                        val id = providerId(result)
                        val rating = if (result.kind == UnifiedSearchRepository.Kind.MOVIE) movieRatings[id]?.rating else seriesRatings[id]?.rating
                        kidsPolicyStore.ratingAllowed(profileId, rating, id)
                    }
                }

                val addonResult = if (isKids) Result.success<List<StremioSearchHit>>(emptyList()) else runCatching { addonSearch.search(trimmed, profileId) }
                unified.rememberSearch(profileId, trimmed)
                SearchState.Ready(
                    results = results,
                    addonItems = addonResult.getOrDefault(emptyList()),
                    partialError = listOfNotNull(unifiedResult.exceptionOrNull()?.message, addonResult.exceptionOrNull()?.message).joinToString(" • ").ifBlank { null },
                )
            }
        } catch (error: Exception) {
            SearchState.Error(error.message ?: "Search failed")
        }
        recentsVersion++
    }

    LazyColumn(
        Modifier.fillMaxSize().background(AstraWaveColors.Background).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AstraWavePageHeader(
                title = if (isKids) "Kids Search" else "Search Everything",
                subtitle = if (isKids) {
                    "AstraWave filters movie and TV results through this profile's rating policy before showing them."
                } else {
                    "One search across movies, TV, live channels, today's sports, radio, music previews, podcasts, personal connections, AstraWave lists and enabled addons."
                },
            )
        }

        if (isKids && !searchAllowed) {
            item {
                AstraWaveStatePanel(
                    title = if (bedtime) "Search paused for bedtime" else "Search disabled in Approved Only mode",
                    message = if (bedtime) "Kids viewing and search are paused during the parent-set bedtime window." else "Browse Kids Movies and Kids TV instead. Individual titles must be parent-approved in Approved Only mode.",
                )
            }
            return@LazyColumn
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; searchNonce = 0 },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(if (isKids) "Movies and shows" else "Title, team, channel, artist, podcast, list…") },
                trailingIcon = {
                    if (query.isNotBlank()) TextButton(onClick = { query = ""; state = SearchState.Idle }) { Text("Clear") }
                },
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { filter ->
                    FilterChip(selected = selectedFilter == filter, onClick = { selectedFilter = filter }, label = { Text(filter.label) })
                }
            }
        }

        if (query.isBlank() && recents.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent Searches", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { unified.clearRecent(profileId); recentsVersion++ }) { Text("Clear") }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recents) { recent ->
                        FilterChip(selected = false, onClick = { query = recent; searchNonce++ }, label = { Text(recent) })
                    }
                }
            }
        }

        if (suggestions.isNotEmpty() && query.length < 3) {
            item {
                Text("Suggestions", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { suggestion ->
                        FilterChip(selected = false, onClick = { query = suggestion; searchNonce++ }, label = { Text(suggestion) })
                    }
                }
            }
        }

        when (val current = state) {
            SearchState.Idle -> item {
                SearchMessage(
                    "Start typing",
                    if (isKids) "Search approved movie and TV metadata." else "Results begin automatically after two characters. Matching uses prefixes, keywords and typo-friendly local ranking.",
                )
            }
            SearchState.Loading -> item {
                Row(Modifier.fillMaxWidth().background(AstraWaveColors.Surface, RoundedCornerShape(16.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = AstraWaveColors.Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(if (isKids) "Searching and checking ratings…" else "Searching AstraWave sources in parallel…", color = AstraWaveColors.SecondaryText)
                }
            }
            is SearchState.Error -> item { SearchMessage("Search unavailable", current.message) }
            is SearchState.Ready -> {
                val filtered = current.results.filter { result ->
                    when (selectedFilter) {
                        SearchFilter.ALL -> true
                        SearchFilter.MOVIES -> result.kind == UnifiedSearchRepository.Kind.MOVIE
                        SearchFilter.TV -> result.kind == UnifiedSearchRepository.Kind.TV
                        SearchFilter.LIVE -> result.kind == UnifiedSearchRepository.Kind.LIVE
                        SearchFilter.SPORTS -> result.kind == UnifiedSearchRepository.Kind.SPORTS
                        SearchFilter.AUDIO -> result.kind in setOf(UnifiedSearchRepository.Kind.RADIO, UnifiedSearchRepository.Kind.MUSIC, UnifiedSearchRepository.Kind.PODCAST)
                        SearchFilter.PERSONAL -> result.kind == UnifiedSearchRepository.Kind.PERSONAL
                        SearchFilter.LISTS -> result.kind == UnifiedSearchRepository.Kind.LIST
                        SearchFilter.ADDONS -> false
                    }
                }
                val showAddons = !isKids && (selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.ADDONS)

                if (filtered.isEmpty() && (!showAddons || current.addonItems.isEmpty())) {
                    item { SearchMessage("No matches", if (isKids) "No results passed this profile's rating and approval rules." else "Try a title, team, channel, artist, genre, network or AstraWave list name.") }
                } else {
                    if (filtered.isNotEmpty()) {
                        item {
                            AstraWaveSectionHeader(title = resultHeading(selectedFilter), subtitle = "${filtered.size} ranked result${if (filtered.size == 1) "" else "s"}")
                        }
                        items(filtered, key = { "${it.kind}:${it.id}" }) { result ->
                            UnifiedResultCard(result, profileId)
                        }
                    }

                    if (showAddons && current.addonItems.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            AstraWaveSectionHeader(title = "From Your Addons", subtitle = "Provider-attributed metadata from enabled compatible addons.")
                        }
                        items(current.addonItems.take(30), key = { "${it.addonId}:${it.item.type}:${it.item.id}" }) { hit ->
                            AddonSearchCard(hit, profileId)
                        }
                    }
                }

                current.partialError?.let { message -> item { SearchMessage("Partial source failure", message) } }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun UnifiedResultCard(result: UnifiedSearchRepository.Result, profileId: String) {
    val context = LocalContext.current
    result.artworkUrl?.let { ArtworkRegistry.register(result.title, it) }
    val canOpen = result.kind !in setOf(UnifiedSearchRepository.Kind.PERSONAL) &&
        (result.kind != UnifiedSearchRepository.Kind.SPORTS || result.streamUrls.isNotEmpty())

    AstraWaveFocusableCard(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).then(if (canOpen) Modifier.clickable {
            when (result.kind) {
                UnifiedSearchRepository.Kind.MOVIE, UnifiedSearchRepository.Kind.TV, UnifiedSearchRepository.Kind.LIBRARY -> {
                    context.startActivity(
                        Intent(context, TitleDetailsActivity::class.java)
                            .putExtra(TitleDetailsActivity.EXTRA_TITLE, result.title)
                            .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, if (result.kind == UnifiedSearchRepository.Kind.TV) "SERIES" else "MOVIE")
                            .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, result.sourceId)
                            .putExtra(TitleDetailsActivity.EXTRA_PROFILE_ID, profileId),
                    )
                }
                UnifiedSearchRepository.Kind.LIVE, UnifiedSearchRepository.Kind.RADIO, UnifiedSearchRepository.Kind.MUSIC, UnifiedSearchRepository.Kind.SPORTS -> {
                    if (result.streamUrls.isNotEmpty()) {
                        context.startActivity(
                            Intent(context, PlayerActivity::class.java)
                                .putExtra(PlayerActivity.EXTRA_URL, result.streamUrls.first())
                                .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(result.streamUrls))
                                .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true)
                                .putExtra(PlayerActivity.EXTRA_PROFILE_ID, profileId),
                        )
                    }
                }
                UnifiedSearchRepository.Kind.PODCAST -> result.sourceId?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                UnifiedSearchRepository.Kind.LIST -> {
                    val activity = if (result.listMediaType == LibraryMediaType.SERIES) TvListDetailActivity::class.java else MovieListDetailActivity::class.java
                    val intent = Intent(context, activity)
                        .putExtra(if (result.listMediaType == LibraryMediaType.SERIES) TvListDetailActivity.EXTRA_TITLE else MovieListDetailActivity.EXTRA_TITLE, result.title)
                        .putExtra(if (result.listMediaType == LibraryMediaType.SERIES) TvListDetailActivity.EXTRA_REASON else MovieListDetailActivity.EXTRA_REASON, result.subtitle.orEmpty())
                        .putExtra(if (result.listMediaType == LibraryMediaType.SERIES) TvListDetailActivity.EXTRA_QUERY else MovieListDetailActivity.EXTRA_QUERY, result.listQuery)
                        .putExtra(if (result.listMediaType == LibraryMediaType.SERIES) TvListDetailActivity.EXTRA_GENRE else MovieListDetailActivity.EXTRA_GENRE, result.listGenre)
                        .putExtra(if (result.listMediaType == LibraryMediaType.SERIES) TvListDetailActivity.EXTRA_PROFILE_ID else MovieListDetailActivity.EXTRA_PROFILE_ID, profileId)
                    context.startActivity(intent)
                }
                UnifiedSearchRepository.Kind.PERSONAL -> Unit
            }
        } else Modifier),
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(150.dp)) { AstraWaveArtwork(result.title, Modifier.fillMaxWidth(), kind = AstraWaveArtworkKind.Backdrop) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(kindLabel(result.kind), color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelMedium)
                Text(result.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                result.subtitle?.takeIf(String::isNotBlank)?.let { Text(it, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium, maxLines = 2) }
                result.description?.takeIf(String::isNotBlank)?.let { Text(it, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                Text(
                    when {
                        result.kind == UnifiedSearchRepository.Kind.PERSONAL -> "Open from Personal Media"
                        result.kind == UnifiedSearchRepository.Kind.SPORTS && result.streamUrls.isEmpty() -> "Event found • no verified channel match yet"
                        result.kind in setOf(UnifiedSearchRepository.Kind.LIVE, UnifiedSearchRepository.Kind.RADIO, UnifiedSearchRepository.Kind.MUSIC, UnifiedSearchRepository.Kind.SPORTS) -> "Play now →"
                        result.kind == UnifiedSearchRepository.Kind.PODCAST -> "Open podcast feed →"
                        result.kind == UnifiedSearchRepository.Kind.LIST -> "Open collection →"
                        else -> "Open details →"
                    },
                    color = if (canOpen) AstraWaveColors.Accent else AstraWaveColors.TertiaryText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun AddonSearchCard(hit: StremioSearchHit, profileId: String) {
    val context = LocalContext.current
    hit.item.posterUrl?.let { ArtworkRegistry.register(hit.item.name, it) }
    val libraryItem = hit.item.toLibraryItemRef(hit.addonId)
    AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
        context.startActivity(
            Intent(context, TitleDetailsActivity::class.java)
                .putExtra(TitleDetailsActivity.EXTRA_TITLE, libraryItem.title)
                .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, libraryItem.type.name)
                .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, libraryItem.sourceId)
                .putExtra(TitleDetailsActivity.EXTRA_PROFILE_ID, profileId),
        )
    }) {
        Column(Modifier.padding(14.dp)) {
            Text(hit.item.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("${hit.addonName} • ${hit.catalogName} • ${hit.item.type}", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelMedium)
            hit.item.description?.takeIf(String::isNotBlank)?.let { Text(it, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
        }
    }
}

private fun resultHeading(filter: SearchFilter): String = when (filter) {
    SearchFilter.ALL -> "Across AstraWave"
    SearchFilter.MOVIES -> "Movies"
    SearchFilter.TV -> "TV Shows"
    SearchFilter.LIVE -> "Live TV"
    SearchFilter.SPORTS -> "Sports Today"
    SearchFilter.AUDIO -> "Music, Radio & Podcasts"
    SearchFilter.PERSONAL -> "Personal Media"
    SearchFilter.LISTS -> "AstraWave Lists"
    SearchFilter.ADDONS -> "Addons"
}

private fun kindLabel(kind: UnifiedSearchRepository.Kind): String = when (kind) {
    UnifiedSearchRepository.Kind.MOVIE -> "MOVIE"
    UnifiedSearchRepository.Kind.TV -> "TV"
    UnifiedSearchRepository.Kind.LIVE -> "LIVE TV"
    UnifiedSearchRepository.Kind.SPORTS -> "SPORTS"
    UnifiedSearchRepository.Kind.RADIO -> "RADIO"
    UnifiedSearchRepository.Kind.MUSIC -> "MUSIC PREVIEW"
    UnifiedSearchRepository.Kind.PODCAST -> "PODCAST"
    UnifiedSearchRepository.Kind.PERSONAL -> "PERSONAL MEDIA"
    UnifiedSearchRepository.Kind.LIST -> "ASTRAWAVE LIST"
    UnifiedSearchRepository.Kind.LIBRARY -> "YOUR LIBRARY"
}

@Composable
private fun SearchMessage(title: String, message: String) {
    AstraWaveStatePanel(title = title, message = message)
}
