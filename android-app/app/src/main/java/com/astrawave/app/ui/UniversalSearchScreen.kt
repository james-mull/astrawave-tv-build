package com.astrawave.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    ALL("All"), MOVIES("Movies"), TV("TV"), LIVE("Live"), SPORTS("Sports"), AUDIO("Audio"), PERSONAL("My Media"), LISTS("Collections"), ADDONS("More")
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
    val device = LocalAstraWaveDeviceClass.current
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
    val filters = remember(isKids) { if (isKids) listOf(SearchFilter.ALL, SearchFilter.MOVIES, SearchFilter.TV) else SearchFilter.entries }

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
        delay(if (searchNonce == 0) 350 else 100)
        state = SearchState.Loading
        state = try {
            withContext(Dispatchers.IO) {
                val unifiedResult = runCatching { unified.search(trimmed, profileId, restrictedToKids = isKids) }
                var results = unifiedResult.getOrDefault(emptyList())
                if (isKids) {
                    results = results.filter { it.kind == UnifiedSearchRepository.Kind.MOVIE || it.kind == UnifiedSearchRepository.Kind.TV }
                    fun providerId(result: UnifiedSearchRepository.Result): String = when {
                        result.sourceId?.startsWith("stremio:", true) == true -> result.sourceId.substringAfterLast(':')
                        result.sourceId?.startsWith("tmdb:", true) == true -> result.sourceId.substringAfterLast(':')
                        result.id.startsWith("library:") -> result.id.removePrefix("library:")
                        else -> result.id
                    }
                    val movies = results.filter { it.kind == UnifiedSearchRepository.Kind.MOVIE }
                    val series = results.filter { it.kind == UnifiedSearchRepository.Kind.TV }
                    val movieRatings = kidsRating.ratings(DynamicCollectionRepository.Media.MOVIE, movies.map(::providerId))
                    val seriesRatings = kidsRating.ratings(DynamicCollectionRepository.Media.SERIES, series.map(::providerId))
                    results = results.filter { result ->
                        val id = providerId(result)
                        val rating = if (result.kind == UnifiedSearchRepository.Kind.MOVIE) movieRatings[id]?.rating else seriesRatings[id]?.rating
                        kidsPolicyStore.ratingAllowed(profileId, rating, id)
                    }
                }
                val addonResult = if (isKids) Result.success(emptyList()) else runCatching { addonSearch.search(trimmed, profileId) }
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
        Modifier.fillMaxSize().background(AstraWaveColors.Background),
        contentPadding = PaddingValues(horizontal = if (device == AstraWaveDeviceClass.PHONE) 16.dp else 24.dp, vertical = if (device == AstraWaveDeviceClass.PHONE) 8.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (device == AstraWaveDeviceClass.PHONE) 10.dp else 14.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = if (device == AstraWaveDeviceClass.PHONE) 4.dp else 10.dp)) {
                Text(
                    if (isKids) "KIDS SEARCH" else "SEARCH",
                    color = AstraWaveColors.PrimaryText,
                    style = if (device == AstraWaveDeviceClass.PHONE) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isKids) "Movies and shows approved for this profile." else "One search across movies, shows, live TV, sports, audio and your media.",
                    color = AstraWaveColors.TertiaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (isKids && !searchAllowed) {
            item {
                AstraWaveStatePanel(
                    title = if (bedtime) "Search paused" else "Search unavailable",
                    message = if (bedtime) "Search is paused during bedtime." else "Use the Kids Movies and Kids TV sections for approved viewing.",
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
                placeholder = { Text(if (isKids) "Search movies and shows" else if (device == AstraWaveDeviceClass.PHONE) "Search" else "Search titles, teams, channels, artists…") },
                trailingIcon = {
                    if (query.isNotBlank()) TextButton(onClick = { query = ""; state = SearchState.Idle }) { Text("Clear") }
                },
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(filters) { filter ->
                    val active = selectedFilter == filter
                    Column(
                        Modifier.clickable { selectedFilter = filter }.padding(horizontal = 5.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            filter.label.uppercase(),
                            color = if (active) AstraWaveColors.PrimaryText else AstraWaveColors.TertiaryText,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.width(if (active) 28.dp else 0.dp).height(2.dp)
                                .background(if (active) AstraWaveColors.FocusRing else Color.Transparent),
                        )
                    }
                }
            }
        }

        if (query.isBlank() && recents.isNotEmpty()) {
            item {
                AstraWaveSectionHeader(
                    title = "Recent Searches",
                    trailing = { TextButton(onClick = { unified.clearRecent(profileId); recentsVersion++ }) { Text("Clear") } },
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recents) { recent ->
                        Text(
                            recent,
                            color = AstraWaveColors.SecondaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { query = recent; searchNonce++ }
                                .padding(horizontal = 6.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }

        if (query.isBlank() && suggestions.isNotEmpty()) {
            item { AstraWaveSectionHeader("Try Searching") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions.take(10)) { suggestion ->
                        Text(
                            suggestion,
                            color = AstraWaveColors.SecondaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { query = suggestion; searchNonce++ }
                                .padding(horizontal = 6.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }

        when (val current = state) {
            SearchState.Idle -> item { SearchMessage("Discover something to watch", if (isKids) "Start typing to search this profile's approved catalog." else "Type at least two characters to begin.") }
            SearchState.Loading -> item {
                Row(
                    Modifier.fillMaxWidth().background(AstraWaveColors.SurfaceRaised, MaterialTheme.shapes.medium).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = AstraWaveColors.Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Searching…", color = AstraWaveColors.SecondaryText)
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
                    item { SearchMessage("No matches", "Try another title, team, channel, artist or collection.") }
                } else {
                    if (filtered.isNotEmpty()) {
                        item { AstraWaveSectionHeader(resultHeading(selectedFilter), "${filtered.size} result${if (filtered.size == 1) "" else "s"}") }
                        items(filtered, key = { "${it.kind}:${it.id}" }) { result -> UnifiedResultCard(result, profileId) }
                    }
                    if (showAddons && current.addonItems.isNotEmpty()) {
                        item { AstraWaveSectionHeader("More Results", "Additional matches from your connected sources") }
                        items(current.addonItems.take(24), key = { "${it.addonId}:${it.item.type}:${it.item.id}" }) { hit -> AddonSearchCard(hit, profileId) }
                    }
                }
                current.partialError?.let { item { SearchMessage("Some results may be missing", "One connected source did not respond.") } }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun UnifiedResultCard(result: UnifiedSearchRepository.Result, profileId: String) {
    val context = LocalContext.current
    result.artworkUrl?.let { ArtworkRegistry.register(result.title, it) }
    val canOpen = when (result.kind) {
        UnifiedSearchRepository.Kind.PERSONAL, UnifiedSearchRepository.Kind.SPORTS -> result.streamUrls.isNotEmpty()
        else -> true
    }
    AstraWaveFocusableCard(
        Modifier.fillMaxWidth().then(if (canOpen) Modifier.clickable {
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
                UnifiedSearchRepository.Kind.LIVE, UnifiedSearchRepository.Kind.RADIO, UnifiedSearchRepository.Kind.MUSIC, UnifiedSearchRepository.Kind.SPORTS, UnifiedSearchRepository.Kind.PERSONAL -> {
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
                    val isSeries = result.listMediaType == LibraryMediaType.SERIES
                    val intent = Intent(context, if (isSeries) TvListDetailActivity::class.java else MovieListDetailActivity::class.java)
                        .putExtra(if (isSeries) TvListDetailActivity.EXTRA_TITLE else MovieListDetailActivity.EXTRA_TITLE, result.title)
                        .putExtra(if (isSeries) TvListDetailActivity.EXTRA_REASON else MovieListDetailActivity.EXTRA_REASON, result.subtitle.orEmpty())
                        .putExtra(if (isSeries) TvListDetailActivity.EXTRA_QUERY else MovieListDetailActivity.EXTRA_QUERY, result.listQuery)
                        .putStringArrayListExtra(if (isSeries) TvListDetailActivity.EXTRA_QUERIES else MovieListDetailActivity.EXTRA_QUERIES, ArrayList(result.listQueries))
                        .putExtra(if (isSeries) TvListDetailActivity.EXTRA_GENRE else MovieListDetailActivity.EXTRA_GENRE, result.listGenre)
                        .putExtra(if (isSeries) TvListDetailActivity.EXTRA_PROFILE_ID else MovieListDetailActivity.EXTRA_PROFILE_ID, profileId)
                    context.startActivity(intent)
                }
            }
        } else Modifier),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(160.dp)) { AstraWaveArtwork(result.title, Modifier.fillMaxWidth(), AstraWaveArtworkKind.Backdrop) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(kindLabel(result.kind), color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(result.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                result.subtitle?.takeIf(String::isNotBlank)?.let { Text(it, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                result.description?.takeIf(String::isNotBlank)?.let { Text(it, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                Text(resultAction(result, canOpen), color = if (canOpen) AstraWaveColors.AccentStrong else AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AddonSearchCard(hit: StremioSearchHit, profileId: String) {
    val context = LocalContext.current
    hit.item.posterUrl?.let { ArtworkRegistry.register(hit.item.name, it) }
    val libraryItem = hit.item.toLibraryItemRef(hit.addonId)
    AstraWaveFocusableCard(
        Modifier.fillMaxWidth().clickable {
            context.startActivity(
                Intent(context, TitleDetailsActivity::class.java)
                    .putExtra(TitleDetailsActivity.EXTRA_TITLE, libraryItem.title)
                    .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, libraryItem.type.name)
                    .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, libraryItem.sourceId)
                    .putExtra(TitleDetailsActivity.EXTRA_PROFILE_ID, profileId),
            )
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(160.dp)) { AstraWaveArtwork(hit.item.name, Modifier.fillMaxWidth(), AstraWaveArtworkKind.Backdrop) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("MORE", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(hit.item.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                hit.item.description?.takeIf(String::isNotBlank)?.let { Text(it, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                Text("Open details ›", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun resultAction(result: UnifiedSearchRepository.Result, canOpen: Boolean): String = when {
    !canOpen && result.kind == UnifiedSearchRepository.Kind.SPORTS -> "No channel available yet"
    !canOpen -> "Unavailable right now"
    result.kind == UnifiedSearchRepository.Kind.PERSONAL -> "Play ›"
    result.kind in setOf(UnifiedSearchRepository.Kind.LIVE, UnifiedSearchRepository.Kind.RADIO, UnifiedSearchRepository.Kind.MUSIC, UnifiedSearchRepository.Kind.SPORTS) -> "Play now ›"
    result.kind == UnifiedSearchRepository.Kind.PODCAST -> "Open ›"
    result.kind == UnifiedSearchRepository.Kind.LIST -> "View collection ›"
    else -> "Open details ›"
}

private fun resultHeading(filter: SearchFilter): String = when (filter) {
    SearchFilter.ALL -> "Top Results"
    SearchFilter.MOVIES -> "Movies"
    SearchFilter.TV -> "TV Shows"
    SearchFilter.LIVE -> "Live TV"
    SearchFilter.SPORTS -> "Sports"
    SearchFilter.AUDIO -> "Audio"
    SearchFilter.PERSONAL -> "My Media"
    SearchFilter.LISTS -> "Collections"
    SearchFilter.ADDONS -> "More Results"
}

private fun kindLabel(kind: UnifiedSearchRepository.Kind): String = when (kind) {
    UnifiedSearchRepository.Kind.MOVIE -> "MOVIE"
    UnifiedSearchRepository.Kind.TV -> "TV SHOW"
    UnifiedSearchRepository.Kind.LIVE -> "LIVE"
    UnifiedSearchRepository.Kind.SPORTS -> "SPORTS"
    UnifiedSearchRepository.Kind.RADIO -> "RADIO"
    UnifiedSearchRepository.Kind.MUSIC -> "MUSIC"
    UnifiedSearchRepository.Kind.PODCAST -> "PODCAST"
    UnifiedSearchRepository.Kind.PERSONAL -> "MY MEDIA"
    UnifiedSearchRepository.Kind.LIST -> "COLLECTION"
    UnifiedSearchRepository.Kind.LIBRARY -> "MY LIST"
}

@Composable
private fun SearchMessage(title: String, message: String) {
    AstraWaveStatePanel(title = title, message = message)
}
