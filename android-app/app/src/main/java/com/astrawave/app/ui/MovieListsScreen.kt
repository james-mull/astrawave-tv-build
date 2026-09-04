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
private fun ordered(id: String, title: String, subtitle: String, titles: List<String>) = MovieListSpec(id, title, subtitle, queries = titles, orderedTitles = titles)
private fun collection(id: String, title: String, subtitle: String, vararg queries: String) = MovieListSpec(id, title, subtitle, queries = queries.toList())

private val weeklyLists = listOf(
    MovieListSpec("weekly-hot", "Weekly Hot List", "What people are watching now", AstraWaveCatalog.TRENDING_MOVIES),
    MovieListSpec("new-releases", "New Releases", "Fresh movie arrivals", AstraWaveCatalog.NOW_PLAYING_MOVIES),
    MovieListSpec("top-rated", "Top Rated Movies", "Audience and critic favorites", AstraWaveCatalog.TOP_RATED_MOVIES),
    MovieListSpec("coming-soon", "Coming Soon", "Movies to keep on your radar", AstraWaveCatalog.UPCOMING_MOVIES),
    MovieListSpec("popular", "Popular Movies", "Big titles right now", AstraWaveCatalog.POPULAR_MOVIES),
)
private val latestLists = listOf(
    MovieListSpec("watchlist", "My Watchlist", "Saved by you", localKind = LocalKind.WATCHLIST),
    MovieListSpec("favorites", "My Favorites", "Movies you marked as favorites", localKind = LocalKind.FAVORITES),
    collection("weekend", "Weekend Picks", "Easy crowd-pleasers for the weekend", "Top Gun Maverick", "Knives Out", "The Martian", "Ocean's Eleven", "Game Night"),
    collection("date-night", "Date Night", "Romance, comedy and stylish crowd-pleasers", "Crazy Rich Asians", "La La Land", "About Time", "The Proposal", "Palm Springs"),
    collection("under-two-hours", "Under 2 Hours", "Fast, satisfying movie-night picks", "Whiplash", "A Quiet Place", "Run Lola Run", "Palm Springs", "Phone Booth"),
)

private val franchiseLists = listOf(
    ordered("harry-potter-order", "Harry Potter in Order", "The eight-film Hogwarts story in release order", listOf("Harry Potter and the Sorcerer's Stone", "Harry Potter and the Chamber of Secrets", "Harry Potter and the Prisoner of Azkaban", "Harry Potter and the Goblet of Fire", "Harry Potter and the Order of the Phoenix", "Harry Potter and the Half-Blood Prince", "Harry Potter and the Deathly Hallows Part 1", "Harry Potter and the Deathly Hallows Part 2")),
    ordered("star-wars-order", "Star Wars Saga", "Core Skywalker films in release order", listOf("Star Wars A New Hope", "The Empire Strikes Back", "Return of the Jedi", "The Phantom Menace", "Attack of the Clones", "Revenge of the Sith", "The Force Awakens", "The Last Jedi", "The Rise of Skywalker")),
    ordered("mission-impossible-order", "Mission: Impossible", "Ethan Hunt missions in release order", listOf("Mission Impossible", "Mission Impossible 2", "Mission Impossible III", "Mission Impossible Ghost Protocol", "Mission Impossible Rogue Nation", "Mission Impossible Fallout", "Mission Impossible Dead Reckoning")),
    ordered("john-wick-order", "John Wick", "The Baba Yaga saga in release order", listOf("John Wick", "John Wick Chapter 2", "John Wick Chapter 3 Parabellum", "John Wick Chapter 4")),
    ordered("insidious-order", "Insidious in Order", "The Insidious films together", listOf("Insidious", "Insidious Chapter 2", "Insidious Chapter 3", "Insidious The Last Key", "Insidious The Red Door")),
    collection("conjuring-universe", "Conjuring Universe", "The Conjuring, Annabelle and The Nun", "The Conjuring", "Annabelle", "The Nun"),
    collection("fast-saga", "Fast Saga", "High-speed franchise viewing", "Fast Furious"),
    collection("james-bond", "James Bond", "007 across generations", "James Bond"),
    collection("marvel-saga", "Marvel Saga", "Avengers, heroes and connected stories", "Avengers", "Iron Man", "Captain America", "Thor", "Guardians of the Galaxy"),
    collection("dc-worlds", "DC Worlds", "Batman, Superman and DC heroes", "Batman", "Superman", "Wonder Woman", "Aquaman", "Justice League"),
    collection("middle-earth", "Middle-earth", "The Lord of the Rings and Hobbit journeys", "Lord of the Rings", "The Hobbit"),
    collection("jurassic", "Jurassic Collection", "Dinosaurs across generations", "Jurassic Park", "Jurassic World"),
    collection("alien-predator", "Alien & Predator", "Sci-fi horror universes", "Alien", "Predator"),
    collection("terminator", "Terminator", "Machines, time travel and Judgment Day", "Terminator"),
    collection("matrix", "The Matrix", "Enter the Matrix universe", "Matrix"),
    collection("rocky-creed", "Rocky & Creed", "Boxing legacy across generations", "Rocky", "Creed"),
    collection("planet-apes", "Planet of the Apes", "Ape saga across eras", "Planet of the Apes"),
    collection("bourne", "Bourne", "Espionage and amnesia-fueled action", "Bourne"),
)

private val genreMoodLists = listOf(
    collection("action-icons", "Action Movies", "Modern action favorites and high-energy franchises", "John Wick", "Mission Impossible", "Mad Max", "Die Hard"),
    collection("superhero-worlds", "Superhero Worlds", "Marvel, DC and comic-book favorites", "Avengers", "Batman", "Spider-Man", "X-Men"),
    collection("sci-fi-worlds", "Sci-Fi Worlds", "Big science-fiction universes", "Star Wars", "Alien", "Terminator", "Matrix", "Blade Runner"),
    collection("family-night", "Family Night", "Easy picks for a family movie night", "Toy Story", "Shrek", "Frozen", "Paddington"),
    collection("animation-hits", "Animation Hits", "Animated favorites across generations", "Toy Story", "Despicable Me", "How to Train Your Dragon", "Kung Fu Panda"),
    collection("horror-night", "Horror Night", "Popular modern horror series", "Conjuring", "Scream", "Insidious", "Halloween"),
    collection("mind-bending", "Mind-Bending Movies", "Twisty science-fiction and psychological favorites", "Inception", "Interstellar", "Memento", "Shutter Island", "Arrival"),
    collection("crime-night", "Crime & Heists", "Heists, mob stories and crime thrillers", "Ocean's Eleven", "Heat", "The Departed", "Goodfellas"),
    collection("comedies", "Laugh Out Loud", "Comedies for an easy movie night", "Superbad", "Bridesmaids", "Game Night", "Step Brothers", "The Hangover"),
    collection("rom-com", "Rom-Com Favorites", "Romantic comedy comfort viewing", "When Harry Met Sally", "Crazy Rich Asians", "10 Things I Hate About You", "Notting Hill"),
    collection("thrillers", "Edge-of-Your-Seat", "Tense thrillers and mysteries", "Prisoners", "Gone Girl", "Sicario", "Nightcrawler", "Zodiac"),
    collection("war", "War Stories", "Epic, intimate and historical war films", "Saving Private Ryan", "1917", "Dunkirk", "Black Hawk Down", "Hacksaw Ridge"),
    collection("westerns", "Westerns", "Modern and classic frontier stories", "Unforgiven", "True Grit", "Tombstone", "The Good the Bad and the Ugly"),
    collection("sports", "Sports Movies", "Underdogs, champions and competition", "Rocky", "Remember the Titans", "Moneyball", "Ford v Ferrari", "Miracle"),
    collection("music", "Music Movies", "Bands, artists and musical stories", "Whiplash", "Almost Famous", "Bohemian Rhapsody", "A Star Is Born", "School of Rock"),
    collection("feel-good", "Feel-Good Movies", "Warm, uplifting and rewatchable", "Chef", "Paddington 2", "The Secret Life of Walter Mitty", "Little Miss Sunshine", "The Intern"),
    collection("tearjerkers", "Bring Tissues", "Emotional dramas and heartfelt stories", "The Green Mile", "Atonement", "Marley and Me", "Manchester by the Sea"),
    collection("survival", "Survival Stories", "Against-the-odds survival", "The Revenant", "Cast Away", "127 Hours", "The Grey", "Society of the Snow"),
)

private val decadeLists = listOf(
    collection("60s-classics", "60s Classics", "Landmark films from the 1960s", "Psycho", "The Good the Bad and the Ugly", "2001 A Space Odyssey", "The Graduate"),
    collection("70s-classics", "70s Classics", "New Hollywood, thrillers and blockbusters", "The Godfather", "Jaws", "Taxi Driver", "Rocky", "Alien"),
    collection("80s-classics", "80s Classics", "Blockbusters, comedy and adventure from the 1980s", "Back to the Future", "The Goonies", "Top Gun", "Ghostbusters", "Die Hard"),
    collection("90s-classics", "90s Classics", "Big-screen favorites from the 1990s", "Jurassic Park", "The Matrix", "Pulp Fiction", "Forrest Gump", "The Lion King"),
    collection("2000s-favorites", "2000s Favorites", "Major hits from the 2000s", "The Dark Knight", "Gladiator", "The Departed", "Finding Nemo", "Casino Royale"),
    collection("2010s-favorites", "2010s Favorites", "Defining movies from the 2010s", "Inception", "Interstellar", "Mad Max Fury Road", "Get Out", "Parasite"),
    collection("2020s-so-far", "2020s So Far", "Standout movies of the decade so far", "Oppenheimer", "Dune Part Two", "Everything Everywhere All at Once", "The Batman", "Top Gun Maverick"),
)

private val filmmakerLists = listOf(
    collection("nolan", "Christopher Nolan", "Puzzle-box blockbusters and ambitious spectacle", "Inception", "Interstellar", "The Dark Knight", "Oppenheimer", "Memento"),
    collection("spielberg", "Steven Spielberg", "Adventure, drama and blockbuster history", "Jaws", "Jurassic Park", "E.T.", "Saving Private Ryan", "Catch Me If You Can"),
    collection("scorsese", "Martin Scorsese", "Crime, character and American epics", "Goodfellas", "Casino", "The Departed", "The Wolf of Wall Street", "Taxi Driver"),
    collection("tarantino", "Quentin Tarantino", "Dialogue, genre remix and cult favorites", "Pulp Fiction", "Kill Bill", "Inglourious Basterds", "Django Unchained", "Once Upon a Time in Hollywood"),
    collection("villeneuve", "Denis Villeneuve", "Atmospheric science-fiction and thrillers", "Arrival", "Blade Runner 2049", "Dune", "Prisoners", "Sicario"),
    collection("fincher", "David Fincher", "Dark precision and psychological thrillers", "Se7en", "Fight Club", "Gone Girl", "Zodiac", "The Social Network"),
    collection("jordan-peele", "Jordan Peele", "Social horror and genre twists", "Get Out", "Us", "Nope"),
)

private val actorLists = listOf(
    collection("tom-cruise", "Tom Cruise", "Action, sci-fi and star-powered hits", "Top Gun Maverick", "Mission Impossible", "Edge of Tomorrow", "Minority Report", "Collateral"),
    collection("denzel", "Denzel Washington", "Thrillers, drama and commanding performances", "Training Day", "Man on Fire", "Inside Man", "Flight", "The Equalizer"),
    collection("leo", "Leonardo DiCaprio", "Modern classics and prestige blockbusters", "Inception", "The Departed", "The Revenant", "Shutter Island", "The Wolf of Wall Street"),
    collection("keanu", "Keanu Reeves", "Action, sci-fi and cult favorites", "John Wick", "The Matrix", "Speed", "Point Break", "Constantine"),
    collection("meryl", "Meryl Streep", "Acclaimed performances across genres", "The Devil Wears Prada", "Doubt", "Julie and Julia", "The Post", "Mamma Mia"),
)

private val studioAwardLists = listOf(
    collection("pixar", "Pixar Favorites", "Family-friendly animation", "Toy Story", "Finding Nemo", "The Incredibles", "Ratatouille", "Coco"),
    collection("dreamworks", "DreamWorks Favorites", "Animated adventures and comedy", "Shrek", "Kung Fu Panda", "How to Train Your Dragon", "Madagascar"),
    collection("ghibli", "Studio Ghibli", "Hand-crafted animated classics", "Spirited Away", "Princess Mononoke", "Howl's Moving Castle", "My Neighbor Totoro"),
    collection("a24", "A24 Favorites", "Distinctive modern indie cinema", "Everything Everywhere All at Once", "Hereditary", "Moonlight", "Uncut Gems", "Past Lives"),
    collection("best-picture", "Best Picture Night", "Acclaimed award-winning movies", "Oppenheimer", "Everything Everywhere All at Once", "Parasite", "Moonlight", "The Shape of Water"),
    collection("oscar-acting", "Oscar Performances", "Award-winning acting showcases", "The Whale", "Joker", "Black Swan", "There Will Be Blood", "Monster"),
    collection("cannes", "Festival Favorites", "International and auteur standouts", "Parasite", "Anatomy of a Fall", "Triangle of Sadness", "The Square", "Shoplifters"),
)

private val documentaryLists = listOf(
    collection("docs", "Great Documentaries", "True stories worth watching", "Free Solo", "The Cove", "Man on Wire", "Won't You Be My Neighbor", "13th"),
    collection("music-docs", "Music Documentaries", "Artists, bands and music history", "Amy", "Searching for Sugar Man", "The Last Waltz", "20 Feet from Stardom"),
    collection("sports-docs", "Sports Documentaries", "Competition, athletes and unforgettable seasons", "Senna", "Hoop Dreams", "Icarus", "When We Were Kings"),
    collection("nature-docs", "Nature & Earth", "Big-screen nature and environmental stories", "March of the Penguins", "My Octopus Teacher", "The Elephant Queen", "Chasing Coral"),
)

private val seasonalLists = listOf(
    collection("halloween", "Halloween Season", "Spooky-season favorites", "Halloween", "Hocus Pocus", "Scream", "The Conjuring", "Trick 'r Treat"),
    collection("holiday", "Holiday Movies", "Comfort movies for the holiday season", "Home Alone", "Elf", "The Santa Clause", "The Polar Express", "National Lampoon's Christmas Vacation"),
    collection("thanksgiving", "Thanksgiving Weekend", "Family, comedy and cozy weekend picks", "Planes Trains and Automobiles", "Knives Out", "The Blind Side", "Fantastic Mr Fox"),
    collection("valentine", "Valentine's Night", "Romance and date-night favorites", "The Notebook", "About Time", "La La Land", "Crazy Stupid Love", "When Harry Met Sally"),
    collection("summer", "Summer Blockbusters", "Big spectacle and adventure", "Jurassic Park", "Top Gun Maverick", "Jaws", "Independence Day", "Twister"),
)

private val cultHiddenLists = listOf(
    collection("cult", "Cult Classics", "Odd, beloved and endlessly quotable", "The Big Lebowski", "Donnie Darko", "Office Space", "The Rocky Horror Picture Show", "Fight Club"),
    collection("hidden-gems", "Hidden Gems", "Excellent movies that deserve another look", "Coherence", "Upgrade", "Sing Street", "The Nice Guys", "Hunt for the Wilderpeople"),
    collection("one-location", "One Location", "Big tension in small spaces", "12 Angry Men", "Buried", "Phone Booth", "The Guilty", "10 Cloverfield Lane"),
    collection("time-travel", "Time Travel", "Loops, paradoxes and second chances", "Back to the Future", "Looper", "Predestination", "Source Code", "About Time"),
    collection("revenge", "Revenge Movies", "Payback, obsession and retribution", "Kill Bill", "Gladiator", "John Wick", "Oldboy", "The Count of Monte Cristo"),
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
    val allSpecs = remember { weeklyLists + latestLists + franchiseLists + genreMoodLists + decadeLists + filmmakerLists + actorLists + studioAwardLists + documentaryLists + seasonalLists + cultHiddenLists + topLists }
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
        return refs.filter { it.type == LibraryMediaType.MOVIE }.map { item -> TmdbItem(abs(item.id.hashCode().toLong()), item.title, spec.subtitle, mediaType = "movie") }
    }
    fun metadataToMovie(item: AstraWaveMetadataGateway.Item): TmdbItem {
        ArtworkRegistry.register(item.name, item.posterUrl ?: item.backdropUrl)
        return TmdbItem(item.id.toLongOrNull() ?: abs(item.id.hashCode().toLong()), item.name, item.description.orEmpty(), mediaType = "movie")
    }
    fun orderItems(spec: MovieListSpec, items: List<TmdbItem>): List<TmdbItem> {
        if (spec.orderedTitles.isEmpty()) return items
        fun norm(v: String) = v.lowercase().filter(Char::isLetterOrDigit)
        val order = spec.orderedTitles.mapIndexed { i, title -> norm(title) to i }
        return items.sortedWith(compareBy<TmdbItem> { item -> val n = norm(item.title); order.firstOrNull { (k, _) -> n.contains(k) || k.contains(n) }?.second ?: Int.MAX_VALUE }.thenBy { it.title })
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
                            val found = spec.queries.flatMap { metadata.search(it) }.filter { it.type.equals("movie", true) }.distinctBy { it.id }.take(80).map(::metadataToMovie)
                            orderItems(spec, found)
                        }
                        MovieListState.Ready(items)
                    } catch (e: Exception) { MovieListState.Error(e.message ?: "Unable to load collection") }
                }
                spec.catalog != null -> {
                    states[spec.id] = MovieListState.Loading
                    states[spec.id] = try { MovieListState.Ready(withContext(Dispatchers.IO) { repository.load(spec.catalog) }.items.filter { it.mediaType != "tv" }.take(50)) }
                    catch (e: Exception) { MovieListState.Error(e.message ?: "Unable to load list") }
                }
            }
        }
    }

    selected?.let { active ->
        MovieListDetail(active, states[active.id] ?: MovieListState.Loading, onBack = { selected = null }) { item ->
            context.startActivity(Intent(context, TitleDetailsActivity::class.java)
                .putExtra(TitleDetailsActivity.EXTRA_TITLE, item.title)
                .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, LibraryMediaType.MOVIE.name)
                .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, "tmdb:${item.id}"))
        }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(AstraWaveColors.Background).padding(horizontal = 24.dp, vertical = 20.dp)) {
        AstraWavePageHeader(title = "Movies", subtitle = "A huge MovieBoxPro-style visual wall with hot lists, franchises, genres, decades, filmmakers, stars, studios, documentaries, seasonal picks and hidden gems.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == "Lists", onClick = { mode = "Lists" }, label = { Text("Lists") })
            FilterChip(selected = mode == "Browse", onClick = { mode = "Browse" }, label = { Text("Browse") })
        }
        Spacer(Modifier.height(18.dp))
        if (mode == "Browse") {
            MovieListRow("Trending Movies", weeklyLists, states) { selected = it }
            Spacer(Modifier.height(22.dp))
            MovieListRow("Popular & New", latestLists, states) { selected = it }
            return@Column
        }
        MovieListRow("Weekly Hot List", weeklyLists, states) { selected = it }; Spacer(Modifier.height(22.dp))
        MovieListRow("Latest List", latestLists, states) { selected = it }; Spacer(Modifier.height(22.dp))
        MovieListRow("Franchises & In Order", franchiseLists, states) { selected = it }; Spacer(Modifier.height(22.dp))
        MovieListRow("Genres & Moods", genreMoodLists.take(if (showMore) genreMoodLists.size else 7), states, showMore = !showMore, onMore = { showMore = true }) { selected = it }; Spacer(Modifier.height(22.dp))
        MovieListRow("By Decade", decadeLists, states) { selected = it }; Spacer(Modifier.height(22.dp))
        MovieListRow("Filmmaker Spotlight", filmmakerLists, states) { selected = it }; Spacer(Modifier.height(22.dp))
        MovieListRow("Movie Stars", actorLists, states) { selected = it }; Spacer(Modifier.height(22.dp))
        MovieListRow("Studios & Awards", studioAwardLists, states) { selected = it }; Spacer(Modifier.height(22.dp))
        if (showMore) {
            MovieListRow("Documentaries", documentaryLists, states) { selected = it }; Spacer(Modifier.height(22.dp))
            MovieListRow("Seasonal & Holiday", seasonalLists, states) { selected = it }; Spacer(Modifier.height(22.dp))
            MovieListRow("Cult & Hidden Gems", cultHiddenLists, states) { selected = it }; Spacer(Modifier.height(22.dp))
        }
        MovieListRow("Top List", topLists, states) { selected = it }; Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun MovieListRow(title: String, specs: List<MovieListSpec>, states: Map<String, MovieListState>, showMore: Boolean = false, onMore: () -> Unit = {}, onSelect: (MovieListSpec) -> Unit) {
    Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        specs.forEach { spec ->
            val items = (states[spec.id] as? MovieListState.Ready)?.items.orEmpty()
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
        if (showMore) AstraWaveFocusableCard(Modifier.width(150.dp).clickable(onClick = onMore)) {
            Column(Modifier.fillMaxWidth().height(165.dp).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("＋", color = AstraWaveColors.Accent, style = MaterialTheme.typography.displaySmall)
                Text("MORE", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.labelLarge)
                Text("Open all collections", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CollectionMosaic(title: String, items: List<TmdbItem>) {
    Box(Modifier.fillMaxWidth().height(140.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val covers = if (items.isEmpty()) listOf<TmdbItem?>(null, null, null) else List(3) { i -> items.getOrNull(i % items.size) }
            covers.forEach { item -> Box(Modifier.weight(1f).fillMaxHeight()) { AstraWaveArtwork(title = item?.title ?: title, modifier = Modifier.fillMaxSize()) } }
        }
        Text(if (items.isEmpty()) "…" else items.size.toString(), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(AstraWaveColors.Background.copy(alpha = 0.82f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
    }
}

@Composable
private fun MovieListDetail(spec: MovieListSpec, state: MovieListState, onBack: () -> Unit, onOpen: (TmdbItem) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(AstraWaveColors.Background).padding(horizontal = 24.dp, vertical = 20.dp)) {
        AstraWavePageHeader(title = spec.title, subtitle = spec.subtitle)
        Spacer(Modifier.height(10.dp)); AstraWaveSecondaryButton(label = "← Back to Lists", onClick = onBack); Spacer(Modifier.height(18.dp))
        when (state) {
            MovieListState.Loading -> AstraWaveStatePanel("Loading ${spec.title}…", "Refreshing this movie collection.", loading = true)
            is MovieListState.Error -> AstraWaveStatePanel("List unavailable", state.message)
            is MovieListState.Ready -> {
                Text("${state.items.size} titles${if (spec.orderedTitles.isNotEmpty()) " • ordered collection" else ""}", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))
                state.items.forEachIndexed { index, item ->
                    AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onOpen(item) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (spec.orderedTitles.isNotEmpty()) Text("${index + 1}", color = AstraWaveColors.Accent, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(34.dp))
                            Box(Modifier.width(118.dp)) { AstraWaveArtwork(title = item.title, modifier = Modifier.fillMaxWidth()) }
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(5.dp))
                                Text(item.overview.ifBlank { "Open for title details and watch options." }, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}
