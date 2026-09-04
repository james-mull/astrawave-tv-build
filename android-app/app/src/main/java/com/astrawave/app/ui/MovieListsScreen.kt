package com.astrawave.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
    val orderedTitles: List<String> = emptyList(),
)

private enum class LocalKind { WATCHLIST, FAVORITES }

private sealed interface MovieListState {
    data object Loading : MovieListState
    data class Ready(val items: List<TmdbItem>) : MovieListState
    data class Error(val message: String) : MovieListState
}

private fun ordered(id: String, title: String, subtitle: String, titles: List<String>) = MovieListSpec(
    id = id,
    title = title,
    subtitle = subtitle,
    queries = titles,
    orderedTitles = titles,
)

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
    ordered("harry-potter-order", "Harry Potter in Order", "The eight-film Hogwarts story in release order", listOf(
        "Harry Potter and the Sorcerer's Stone", "Harry Potter and the Chamber of Secrets", "Harry Potter and the Prisoner of Azkaban",
        "Harry Potter and the Goblet of Fire", "Harry Potter and the Order of the Phoenix", "Harry Potter and the Half-Blood Prince",
        "Harry Potter and the Deathly Hallows Part 1", "Harry Potter and the Deathly Hallows Part 2",
    )),
    ordered("star-wars-order", "Star Wars Saga", "Core Skywalker films in release order", listOf(
        "Star Wars A New Hope", "The Empire Strikes Back", "Return of the Jedi", "The Phantom Menace", "Attack of the Clones",
        "Revenge of the Sith", "The Force Awakens", "The Last Jedi", "The Rise of Skywalker",
    )),
    ordered("mission-impossible-order", "Mission: Impossible", "Ethan Hunt missions in release order", listOf(
        "Mission Impossible", "Mission Impossible 2", "Mission Impossible III", "Mission Impossible Ghost Protocol",
        "Mission Impossible Rogue Nation", "Mission Impossible Fallout", "Mission Impossible Dead Reckoning",
    )),
    ordered("john-wick-order", "John Wick", "The Baba Yaga saga in release order", listOf("John Wick", "John Wick Chapter 2", "John Wick Chapter 3 Parabellum", "John Wick Chapter 4")),
    ordered("insidious-order", "Insidious in Order", "The Insidious films together", listOf("Insidious", "Insidious Chapter 2", "Insidious Chapter 3", "Insidious The Last Key", "Insidious The Red Door")),
    MovieListSpec("conjuring-universe", "Conjuring Universe", "The Conjuring, Annabelle and The Nun", queries = listOf("The Conjuring", "Annabelle", "The Nun")),
    MovieListSpec("fast-saga", "Fast Saga", "High-speed franchise viewing", queries = listOf("Fast Furious")),
    MovieListSpec("james-bond", "James Bond", "007 across generations", queries = listOf("James Bond")),
)

private val genreMoodLists = listOf(
    MovieListSpec("action-icons", "Action Movies", "Modern action favorites and high-energy franchises", queries = listOf("John Wick", "Mission Impossible", "Mad Max", "Die Hard")),
    MovieListSpec("superhero-worlds", "Superhero Worlds", "Marvel, DC and comic-book favorites", queries = listOf("Avengers", "Batman", "Spider-Man", "X-Men")),
    MovieListSpec("sci-fi-worlds", "Sci-Fi Worlds", "Big science-fiction universes", queries = listOf("Star Wars", "Alien", "Predator", "Terminator", "Matrix")),
    MovieListSpec("family-night", "Family Night", "Easy picks for a family movie night", queries = listOf("Toy Story", "Shrek", "Frozen", "Paddington")),
    MovieListSpec("animation-hits", "Animation Hits", "Animated favorites across generations", queries = listOf("Toy Story", "Despicable Me", "How to Train Your Dragon", "Kung Fu Panda")),
    MovieListSpec("horror-night", "Horror Night", "Popular modern horror series", queries = listOf("Conjuring", "Scream", "Insidious", "Halloween")),
    MovieListSpec("mind-bending", "Mind-Bending Movies", "Twisty science-fiction and psychological favorites", queries = listOf("Inception", "Interstellar", "Memento", "Shutter Island", "Arrival")),
    MovieListSpec("crime-night", "Crime & Heists", "Heists, mob stories and crime thrillers", queries = listOf("Ocean's Eleven", "Heat", "The Departed", "Goodfellas")),
)

private val decadeLists = listOf(
    MovieListSpec("80s-classics", "80s Classics", "Blockbusters, comedy and adventure from the 1980s", queries = listOf("Back to the Future", "The Goonies", "Top Gun", "Ghostbusters", "Die Hard")),
    MovieListSpec("90s-classics", "90s Classics", "Big-screen favorites from the 1990s", queries = listOf("Jurassic Park", "The Matrix", "Pulp Fiction", "Forrest Gump", "The Lion King")),
    MovieListSpec("2000s-favorites", "2000s Favorites", "Major hits from the 2000s", queries = listOf("The Dark Knight", "Gladiator", "The Departed", "Finding Nemo", "Casino Royale")),
    MovieListSpec("2010s-favorites", "2010s Favorites", "Defining movies from the 2010s", queries = listOf("Inception", "Interstellar", "Mad Max Fury Road", "Get Out", "Parasite")),
)

private val studioAwardLists = listOf(
    MovieListSpec("pixar", "Pixar Favorites", "A family-friendly animation collection", queries = listOf("Toy Story", "Finding Nemo", "The Incredibles", "Ratatouille", "Coco")),
    MovieListSpec("dreamworks", "DreamWorks Favorites", "Animated adventures and comedy", queries = listOf("Shrek", "Kung Fu Panda", "How to Train Your Dragon", "Madagascar")),
    MovieListSpec("best-picture", "Best Picture Night", "Acclaimed award-winning movies", queries = listOf("Oppenheimer", "Everything Everywhere All at Once", "Parasite", "Green Book", "The Shape of Water")),
    MovieListSpec("director-night", "Director Showcase", "Signature films from major filmmakers", queries = listOf("Christopher Nolan", "Quentin Tarantino", "Martin Scorsese", "Denis Villeneuve")),
)

private val seasonalLists = listOf(
    MovieListSpec("halloween", "Halloween Season", "Spooky-season favorites", queries = listOf("Halloween", "Hocus Pocus", "Scream", "The Conjuring")),
    MovieListSpec("holiday", "Holiday Movies", "Comfort movies for the holiday season", queries = listOf("Home Alone", "Elf", "The Santa Clause", "The Polar Express")),
    MovieListSpec("summer", "Summer Blockbusters", "Big spectacle and adventure", queries = listOf("Jurassic Park", "Top Gun Maverick", "Jaws", "Independence Day")),
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
    val allSpecs = remember {
        weeklyLists + latestLists + franchiseLists + genreMoodLists + decadeLists + studioAwardLists + seasonalLists + topLists
    }
    val states = remember { mutableStateMapOf<String, MovieListState>() }
    var selected by remember { mutableStateOf<MovieListSpec?>(null) }
    var mode by remember { mutableStateOf("Lists") }
    var showMore by remember { mutableStateOf(false) }

    fun localItems(spec: MovieListSpec): List<TmdbItem> {
        val refs: List<LibraryItemRef> = when (spec.localKind) {
            LocalKind.WATCHLIST -> library.watchlist(profileId).map { it.item }
            LocalKind.FAVORITES -> library.favorites(profileId).map { it.item }
            null -> emptyList()
        }
        return refs.filter { it.type == LibraryMediaType.MOVIE }.map { item ->
            TmdbItem(abs(item.id.hashCode().toLong()), item.title, spec.subtitle, mediaType = "movie")
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

    fun orderItems(spec: MovieListSpec, items: List<TmdbItem>): List<TmdbItem> {
        if (spec.orderedTitles.isEmpty()) return items
        fun normalize(value: String) = value.lowercase().filter(Char::isLetterOrDigit)
        val order = spec.orderedTitles.mapIndexed { index, title -> normalize(title) to index }
        return items.sortedWith(compareBy<TmdbItem> { item ->
            val normalized = normalize(item.title)
            order.firstOrNull { (key, _) -> normalized.contains(key) || key.contains(normalized) }?.second ?: Int.MAX_VALUE
        }.thenBy { it.title })
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
                            val found = spec.queries.flatMap { query -> metadata.search(query) }
                                .filter { it.type.equals("movie", true) }
                                .distinctBy { it.id }
                                .take(60)
                                .map(::metadataToMovie)
                            orderItems(spec, found)
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
                        MovieListState.Ready(page.items.filter { it.mediaType != "tv" }.take(40))
                    } catch (error: Exception) {
                        MovieListState.Error(error.message ?: "Unable to load list")
                    }
                }
            }
        }
    }

    selected?.let { active ->
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
            subtitle = "A TV-first collection wall with curated franchises, moods, decades, studios and seasonal lists.",
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == "Lists", onClick = { mode = "Lists" }, label = { Text("Lists") })
            FilterChip(selected = mode == "Browse", onClick = { mode = "Browse" }, label = { Text("Browse") })
        }
        Spacer(Modifier.height(18.dp))

        if (mode == "Browse") {
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
        MovieListRow("Genres & Moods", genreMoodLists.take(if (showMore) genreMoodLists.size else 5), states, showMore = !showMore, onMore = { showMore = true }) { selected = it }
        Spacer(Modifier.height(22.dp))

        if (showMore) {
            MovieListRow("By Decade", decadeLists, states) { selected = it }
            Spacer(Modifier.height(22.dp))
            MovieListRow("Studios & Awards", studioAwardLists, states) { selected = it }
            Spacer(Modifier.height(22.dp))
            MovieListRow("Seasonal", seasonalLists, states) { selected = it }
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
            AstraWaveFocusableCard(Modifier.width(250.dp).clickable { onSelect(spec) }) {
                Column {
                    CollectionMosaic(spec.title, items)
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
                    Modifier.fillMaxWidth().height(165.dp).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("＋", color = AstraWaveColors.Accent, style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(6.dp))
                    Text("MORE", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("Open the full collection library", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CollectionMosaic(title: String, items: List<TmdbItem>) {
    Box(Modifier.fillMaxWidth().height(140.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val covers = if (items.isEmpty()) listOf<TmdbItem?>(null, null, null) else List(3) { index -> items.getOrNull(index % items.size) }
            covers.forEach { item ->
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AstraWaveArtwork(title = item?.title ?: title, modifier = Modifier.fillMaxSize())
                }
            }
        }
        Text(
            if (items.isEmpty()) "…" else items.size.toString(),
            color = AstraWaveColors.PrimaryText,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.TopStart)
                .padding(8.dp)
                .background(AstraWaveColors.Background.copy(alpha = 0.82f), RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 4.dp),
        )
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
                Text("${state.items.size} titles${if (spec.orderedTitles.isNotEmpty()) " • ordered collection" else ""}", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))
                state.items.forEachIndexed { index, item ->
                    AstraWaveFocusableCard(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onOpen(item) },
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (spec.orderedTitles.isNotEmpty()) {
                                Text("${index + 1}", color = AstraWaveColors.Accent, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(34.dp))
                            }
                            Box(Modifier.width(118.dp)) { AstraWaveArtwork(title = item.title, modifier = Modifier.fillMaxWidth()) }
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
