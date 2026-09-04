package com.astrawave.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.TitleDetailsActivity
import com.astrawave.app.core.LibraryItemRef
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.data.AstraWaveCatalog
import com.astrawave.app.data.AstraWaveMetadataGateway
import com.astrawave.app.data.ArtworkRegistry
import com.astrawave.app.data.LocalLibraryStore
import com.astrawave.app.data.TmdbItem
import com.astrawave.app.data.ZeroConfigCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

private data class MovieListSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val catalog: AstraWaveCatalog? = null,
    val localKind: LocalKind? = null,
    val queries: List<String> = emptyList(),
)

private enum class LocalKind { WATCHLIST, FAVORITES }

private sealed interface MovieListState {
    data object Loading : MovieListState
    data class Ready(val items: List<TmdbItem>) : MovieListState
    data class Error(val message: String) : MovieListState
}

private val weeklyLists = listOf(
    MovieListSpec("weekly-hot", "Weekly Hot List", "What people are watching now", AstraWaveCatalog.TRENDING_MOVIES),
    MovieListSpec("new-releases", "New Releases", "Fresh movie arrivals", AstraWaveCatalog.NOW_PLAYING_MOVIES),
    MovieListSpec("top-rated", "Top Rated Movies", "Audience and critic favorites", AstraWaveCatalog.TOP_RATED_MOVIES),
    MovieListSpec("coming-soon", "Coming Soon", "Movies to keep on your radar", AstraWaveCatalog.UPCOMING_MOVIES),
)

private val latestLists = listOf(
    MovieListSpec("watchlist", "My Watchlist", "Saved by you", localKind = LocalKind.WATCHLIST),
    MovieListSpec("favorites", "My Favorites", "Movies you marked as favorites", localKind = LocalKind.FAVORITES),
    MovieListSpec("popular", "Popular Movies", "Big titles right now", AstraWaveCatalog.POPULAR_MOVIES),
    MovieListSpec("trending-more", "Trending Now", "More current movie picks", AstraWaveCatalog.TRENDING_MOVIES),
)

private val franchiseLists = listOf(
    MovieListSpec("wizarding-world", "Wizarding World", "Harry Potter & Fantastic Beasts in one collection", queries = listOf("Harry Potter", "Fantastic Beasts")),
    MovieListSpec("star-wars", "Star Wars Galaxy", "The Skywalker saga and connected stories", queries = listOf("Star Wars")),
    MovieListSpec("mission-impossible", "Mission: Impossible", "Ethan Hunt missions in one place", queries = listOf("Mission Impossible")),
    MovieListSpec("fast-saga", "Fast Saga", "High-speed franchise viewing", queries = listOf("Fast Furious")),
    MovieListSpec("conjuring-universe", "Conjuring Universe", "The Conjuring, Annabelle and The Nun", queries = listOf("Conjuring", "Annabelle", "The Nun")),
    MovieListSpec("insidious", "Insidious in Order", "Follow the Insidious films together", queries = listOf("Insidious")),
    MovieListSpec("john-wick", "John Wick", "The Baba Yaga collection", queries = listOf("John Wick")),
    MovieListSpec("james-bond", "James Bond", "007 across generations", queries = listOf("James Bond")),
)

private val themedLists = listOf(
    MovieListSpec("action-icons", "Action Movies", "Modern action favorites and high-energy franchises", queries = listOf("John Wick", "Mission Impossible", "Mad Max", "Die Hard")),
    MovieListSpec("superhero-worlds", "Superhero Worlds", "Marvel, DC and comic-book favorites", queries = listOf("Avengers", "Batman", "Spider-Man", "X-Men")),
    MovieListSpec("sci-fi-worlds", "Sci-Fi Worlds", "Big science-fiction universes", queries = listOf("Star Wars", "Alien", "Predator", "Terminator", "Matrix")),
    MovieListSpec("family-night", "Family Night", "Easy picks for a family movie night", queries = listOf("Toy Story", "Shrek", "Frozen", "Paddington")),
    MovieListSpec("animation-hits", "Animation Hits", "Animated favorites across generations", queries = listOf("Toy Story", "Despicable Me", "How to Train Your Dragon", "Kung Fu Panda")),
    MovieListSpec("horror-night", "Horror Night", "Popular modern horror series", queries = listOf("Conjuring", "Scream", "Insidious", "Halloween")),
    MovieListSpec("mind-bending", "Mind-Bending Movies", "Twisty science-fiction and psychological favorites", queries = listOf("Inception", "Interstellar", "Memento", "Shutter Island", "Arrival")),
    MovieListSpec("crime-night", "Crime & Heists", "Heists, mob stories and crime thrillers", queries = listOf("Ocean's Eleven", "Heat", "The Departed", "Goodfellas")),
)

private val topLists = listOf(
    MovieListSpec("top-100", "Top Movies", "AstraWave top movie picks", AstraWaveCatalog.TOP_RATED_MOVIES),
    MovieListSpec("must-watch", "Must Watch", "High-interest movies for your queue", AstraWaveCatalog.POPULAR_MOVIES),
    MovieListSpec("recent-hits", "Recent Hits", "Current releases getting attention", AstraWaveCatalog.NOW_PLAYING_MOVIES),
)

@Composable
fun MovieListsScreen(profileId: String = "default") {
    val context = LocalContext.current
    val repository = remember { ZeroConfigCatalogRepository() }
    val metadata = remember { AstraWaveMetadataGateway() }
    val library = remember { LocalLibraryStore(context) }
    val allSpecs = remember { weeklyLists + latestLists + franchiseLists + themedLists + topLists }
    val states = remember { mutableStateMapOf<String, MovieListState>() }
    var selected by remember { mutableStateOf<MovieListSpec?>(null) }
    var mode by remember { mutableStateOf("Lists") }
    var showAllCollections by remember { mutableStateOf(false) }

    fun localItems(spec: MovieListSpec): List<TmdbItem> {
        val refs: List<LibraryItemRef> = when (spec.localKind) {
            LocalKind.WATCHLIST -> library.watchlist(profileId).map { it.item }
            LocalKind.FAVORITES -> library.favorites(profileId).map { it.item }
            null -> emptyList()
        }
        return refs.filter { it.type == LibraryMediaType.MOVIE }.map { item ->
            TmdbItem(
                id = abs(item.id.hashCode().toLong()),
                title = item.title,
                overview = spec.subtitle,
                mediaType = "movie",
            )
        }
    }

    fun metadataToMovie(item: AstraWaveMetadataGateway.Item): TmdbItem {
        ArtworkRegistry.register(item.name, item.posterUrl ?: item.backdropUrl)
        return TmdbItem(
            id = item.id.toLongOrNull() ?: abs(item.id.hashCode().toLong()),
            title = item.name,
            overview = item.description.orEmpty(),
            mediaType = "movie",
        )
    }

    LaunchedEffect(Unit) {
        allSpecs.forEach { spec ->
            if (states.containsKey(spec.id)) return@forEach
            when {
                spec.localKind != null -> states[spec.id] = MovieListState.Ready(localItems(spec))
                spec.queries.isNotEmpty() -> {
                    states[spec.id] = MovieListState.Loading
                    states[spec.id] = try {
                        val items = withContext(Dispatchers.IO) {
                            spec.queries.flatMap { query -> metadata.search(query) }
                                .filter { it.type.equals("movie", true) }
                                .distinctBy { it.id }
                                .take(40)
                                .map(::metadataToMovie)
                        }
                        MovieListState.Ready(items)
                    } catch (error: Exception) {
                        MovieListState.Error(error.message ?: "Unable to load collection")
                    }
                }
                spec.catalog != null -> {
                    states[spec.id] = MovieListState.Loading
                    states[spec.id] = try {
                        val page = withContext(Dispatchers.IO) { repository.load(spec.catalog) }
                        MovieListState.Ready(page.items.filter { it.mediaType != "tv" }.take(30))
                    } catch (error: Exception) {
                        MovieListState.Error(error.message ?: "Unable to load list")
                    }
                }
            }
        }
    }

    val active = selected
    if (active != null) {
        MovieListDetail(
            spec = active,
            state = states[active.id] ?: MovieListState.Loading,
            onBack = { selected = null },
            onOpen = { item ->
                context.startActivity(
                    Intent(context, TitleDetailsActivity::class.java)
                        .putExtra(TitleDetailsActivity.EXTRA_TITLE, item.title)
                        .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, LibraryMediaType.MOVIE.name)
                        .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, "tmdb:${item.id}"),
                )
            },
        )
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        AstraWavePageHeader(
            title = "Movies",
            subtitle = "Visual collections, franchises, themed picks and traditional movie browsing in one TV-first library.",
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == "Lists", onClick = { mode = "Lists" }, label = { Text("Lists") })
            FilterChip(selected = mode == "Browse", onClick = { mode = "Browse" }, label = { Text("Browse") })
        }
        Spacer(Modifier.height(18.dp))

        if (mode == "Browse") {
            Text("Browse", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Quick access to AstraWave's standard movie discovery rows.",
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(18.dp))
            MovieListRow("Trending Movies", weeklyLists.take(1), states) { selected = it }
            Spacer(Modifier.height(22.dp))
            MovieListRow("Popular & New", listOf(latestLists[2], topLists[2], weeklyLists[2]), states) { selected = it }
            return@Column
        }

        MovieListRow("Weekly Hot List", weeklyLists, states) { selected = it }
        Spacer(Modifier.height(22.dp))
        MovieListRow("Latest List", latestLists, states) { selected = it }
        Spacer(Modifier.height(22.dp))
        MovieListRow("Franchises & In Order", franchiseLists, states) { selected = it }
        Spacer(Modifier.height(22.dp))
        MovieListRow("Collections", themedLists.take(if (showAllCollections) themedLists.size else 5), states, showMore = !showAllCollections, onMore = { showAllCollections = true }) { selected = it }
        Spacer(Modifier.height(22.dp))
        if (showAllCollections) {
            MovieListRow("More Collections", themedLists.drop(5), states) { selected = it }
            Spacer(Modifier.height(22.dp))
        }
        MovieListRow("Top List", topLists, states) { selected = it }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun MovieListRow(
    title: String,
    specs: List<MovieListSpec>,
    states: Map<String, MovieListState>,
    showMore: Boolean = false,
    onMore: () -> Unit = {},
    onSelect: (MovieListSpec) -> Unit,
) {
    Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        specs.forEach { spec ->
            val state = states[spec.id]
            val items = (state as? MovieListState.Ready)?.items.orEmpty()
            val count = items.size
            AstraWaveFocusableCard(
                Modifier.width(236.dp).clickable { onSelect(spec) },
            ) {
                Column {
                    Box(Modifier.fillMaxWidth()) {
                        AstraWaveArtwork(
                            title = items.firstOrNull()?.title ?: spec.title,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            if (state is MovieListState.Loading) "…" else count.toString(),
                            color = AstraWaveColors.PrimaryText,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(AstraWaveColors.Background.copy(alpha = 0.78f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(spec.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Spacer(Modifier.height(3.dp))
                    Text(spec.subtitle, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }
        if (showMore) {
            AstraWaveFocusableCard(Modifier.width(150.dp).clickable(onClick = onMore)) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("＋", color = AstraWaveColors.Accent, style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(6.dp))
                    Text("MORE", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("Open the full curated collection library.", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MovieListDetail(
    spec: MovieListSpec,
    state: MovieListState,
    onBack: () -> Unit,
    onOpen: (TmdbItem) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        AstraWavePageHeader(title = spec.title, subtitle = spec.subtitle)
        Spacer(Modifier.height(10.dp))
        AstraWaveSecondaryButton(label = "← Back to Lists", onClick = onBack)
        Spacer(Modifier.height(18.dp))
        when (state) {
            MovieListState.Loading -> AstraWaveStatePanel("Loading ${spec.title}…", "Refreshing this movie collection.", loading = true)
            is MovieListState.Error -> AstraWaveStatePanel("List unavailable", state.message)
            is MovieListState.Ready -> {
                Text("${state.items.size} titles", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))
                state.items.forEach { item ->
                    AstraWaveFocusableCard(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onOpen(item) },
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(118.dp)) {
                                AstraWaveArtwork(title = item.title, modifier = Modifier.fillMaxWidth())
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    item.overview.ifBlank { "Open for title details and watch options." },
                                    color = AstraWaveColors.SecondaryText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}
