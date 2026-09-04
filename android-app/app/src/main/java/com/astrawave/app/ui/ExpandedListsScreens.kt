package com.astrawave.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.TitleDetailsActivity
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.data.AstraWaveMetadataGateway
import com.astrawave.app.data.ArtworkRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class MassiveListSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val queries: List<String>,
)

private sealed interface MassiveListState {
    data object Loading : MassiveListState
    data class Ready(val items: List<AstraWaveMetadataGateway.Item>) : MassiveListState
    data class Error(val message: String) : MassiveListState
}

private fun m(id: String, title: String, subtitle: String, vararg q: String) =
    MassiveListSpec(id, title, subtitle, q.toList())

private val movieRows: List<Pair<String, List<MassiveListSpec>>> = listOf(
    "Trending & Fresh" to listOf(
        m("mv-weekly-hot", "Weekly Hot List", "What everyone is watching", "Oppenheimer", "Dune Part Two", "Top Gun Maverick", "Barbie"),
        m("mv-new-week", "New This Week", "Fresh movie-night picks", "Civil War", "Furiosa", "Challengers", "The Fall Guy"),
        m("mv-most-talked", "Most Talked About", "Big conversation movies", "Everything Everywhere All at Once", "Saltburn", "Poor Things", "Anatomy of a Fall"),
        m("mv-streaming-now", "Streaming Now", "Popular current home-viewing picks", "The Killer", "Glass Onion", "Prey", "Air"),
        m("mv-coming-soon", "Coming Soon", "Movies to keep on your radar", "Avatar Fire and Ash", "The Batman Part II", "Avengers Secret Wars"),
        m("mv-top-100", "Top 100", "AstraWave essentials", "The Godfather", "The Dark Knight", "Pulp Fiction", "Parasite", "Whiplash"),
    ),
    "Franchises & Universes" to listOf(
        m("mv-harry", "Harry Potter in Order", "Wizarding World marathon", "Harry Potter"),
        m("mv-starwars", "Star Wars", "Galaxy-spanning saga", "Star Wars"),
        m("mv-marvel", "Marvel Universe", "Heroes and connected stories", "Avengers", "Iron Man", "Captain America", "Thor"),
        m("mv-dc", "DC Worlds", "Batman, Superman and more", "Batman", "Superman", "Wonder Woman", "Aquaman"),
        m("mv-fast", "Fast Saga", "High-speed franchise viewing", "Fast Furious"),
        m("mv-mi", "Mission: Impossible", "Ethan Hunt missions", "Mission Impossible"),
        m("mv-jw", "John Wick", "The Baba Yaga collection", "John Wick"),
        m("mv-conjuring", "Conjuring Universe", "Conjuring, Annabelle and Nun", "The Conjuring", "Annabelle", "The Nun"),
        m("mv-insidious", "Insidious", "The complete horror series", "Insidious"),
        m("mv-jurassic", "Jurassic", "Dinosaurs across generations", "Jurassic Park", "Jurassic World"),
        m("mv-middle-earth", "Middle-earth", "LOTR and Hobbit", "Lord of the Rings", "The Hobbit"),
        m("mv-bond", "James Bond", "007 across generations", "James Bond"),
        m("mv-rocky", "Rocky & Creed", "Boxing legacy", "Rocky", "Creed"),
        m("mv-matrix", "The Matrix", "Enter the Matrix", "Matrix"),
        m("mv-alien", "Alien & Predator", "Sci-fi horror universes", "Alien", "Predator"),
        m("mv-terminator", "Terminator", "Machines and time travel", "Terminator"),
        m("mv-apes", "Planet of the Apes", "Ape saga across eras", "Planet of the Apes"),
        m("mv-bourne", "Bourne", "Espionage action", "Bourne"),
    ),
    "Genres" to listOf(
        m("mv-action", "Action", "High-energy favorites", "John Wick", "Die Hard", "Mad Max Fury Road", "Extraction"),
        m("mv-comedy", "Comedy", "Laugh-out-loud picks", "Superbad", "Bridesmaids", "Game Night", "The Hangover"),
        m("mv-horror", "Horror", "Scares for movie night", "The Conjuring", "Hereditary", "Scream", "Talk to Me"),
        m("mv-scifi", "Sci-Fi", "Big ideas and worlds", "Interstellar", "Arrival", "Blade Runner 2049", "Dune"),
        m("mv-fantasy", "Fantasy", "Magic and epic adventure", "Lord of the Rings", "Pan's Labyrinth", "Stardust", "The Green Knight"),
        m("mv-thriller", "Thrillers", "Tense and twisty", "Gone Girl", "Prisoners", "Sicario", "Nightcrawler"),
        m("mv-crime", "Crime", "Mob, cops and underworld", "Goodfellas", "Heat", "The Departed", "Casino"),
        m("mv-heist", "Heists", "Plans, crews and scores", "Ocean's Eleven", "Inside Man", "The Town", "Logan Lucky"),
        m("mv-romance", "Romance", "Love stories", "About Time", "The Notebook", "La La Land", "Past Lives"),
        m("mv-romcom", "Rom-Com", "Easy date-night viewing", "Crazy Rich Asians", "Notting Hill", "10 Things I Hate About You", "The Proposal"),
        m("mv-drama", "Drama", "Character-driven stories", "Manchester by the Sea", "Moonlight", "The Social Network", "There Will Be Blood"),
        m("mv-war", "War", "Epic and intimate war stories", "Saving Private Ryan", "1917", "Dunkirk", "Hacksaw Ridge"),
        m("mv-western", "Westerns", "Frontier stories", "Unforgiven", "True Grit", "Tombstone", "Hostiles"),
        m("mv-sports", "Sports", "Underdogs and champions", "Rocky", "Moneyball", "Ford v Ferrari", "Miracle"),
        m("mv-musical", "Music & Musicals", "Songs, bands and performances", "Whiplash", "La La Land", "A Star Is Born", "Almost Famous"),
        m("mv-animation", "Animation", "Animated favorites", "Toy Story", "Spider-Man Into the Spider-Verse", "Coco", "How to Train Your Dragon"),
        m("mv-family", "Family", "All-ages movie night", "Paddington", "The Incredibles", "Night at the Museum", "School of Rock"),
        m("mv-documentary", "Documentaries", "True stories worth watching", "Free Solo", "13th", "The Cove", "Man on Wire"),
    ),
    "Moods & Occasions" to listOf(
        m("mv-feelgood", "Feel-Good", "Warm and uplifting", "Chef", "Paddington 2", "The Intern", "Little Miss Sunshine"),
        m("mv-mindbend", "Mind-Bending", "Twists and big ideas", "Inception", "Memento", "Shutter Island", "Coherence"),
        m("mv-comfort", "Comfort Movies", "Easy rewatch favorites", "The Devil Wears Prada", "The Holiday", "School of Rock", "Chef"),
        m("mv-tearjerker", "Bring Tissues", "Emotional movie night", "The Green Mile", "Atonement", "Marley and Me", "Blue Valentine"),
        m("mv-date", "Date Night", "Stylish romance and comedy", "Crazy Rich Asians", "La La Land", "Palm Springs", "About Time"),
        m("mv-family-night", "Family Night", "Everyone can watch", "Toy Story", "Shrek", "Frozen", "Paddington"),
        m("mv-rainy", "Rainy Day", "Cozy, immersive picks", "Harry Potter", "Knives Out", "The Princess Bride", "Little Women"),
        m("mv-late-night", "Late Night", "Dark, weird and atmospheric", "Nightcrawler", "Drive", "Enemy", "Under the Skin"),
        m("mv-adrenaline", "Adrenaline Rush", "Nonstop action", "Mad Max Fury Road", "John Wick", "The Raid", "Mission Impossible Fallout"),
        m("mv-slowburn", "Slow Burn", "Patient, rewarding thrillers", "Zodiac", "Prisoners", "The Witch", "No Country for Old Men"),
        m("mv-underdog", "Underdog Stories", "Against-the-odds favorites", "Rocky", "Rudy", "Moneyball", "The Pursuit of Happyness"),
        m("mv-survival", "Survival", "Fight to make it out", "The Revenant", "Cast Away", "127 Hours", "Society of the Snow"),
    ),
    "By Decade" to listOf(
        m("mv-50s", "50s Classics", "Golden-age favorites", "12 Angry Men", "Rear Window", "Singin' in the Rain", "Seven Samurai"),
        m("mv-60s", "60s Classics", "Landmark 1960s films", "Psycho", "2001 A Space Odyssey", "The Graduate", "Lawrence of Arabia"),
        m("mv-70s", "70s Classics", "New Hollywood and blockbusters", "The Godfather", "Jaws", "Taxi Driver", "Rocky"),
        m("mv-80s", "80s Classics", "Blockbusters and adventure", "Back to the Future", "Top Gun", "Ghostbusters", "Die Hard"),
        m("mv-90s", "90s Classics", "Big-screen 90s favorites", "Jurassic Park", "Pulp Fiction", "Forrest Gump", "The Matrix"),
        m("mv-2000s", "2000s", "Defining 2000s movies", "Gladiator", "The Dark Knight", "Casino Royale", "Finding Nemo"),
        m("mv-2010s", "2010s", "Defining 2010s movies", "Inception", "Interstellar", "Get Out", "Parasite"),
        m("mv-2020s", "2020s So Far", "Standouts of the decade", "Oppenheimer", "Dune Part Two", "The Batman", "Everything Everywhere All at Once"),
    ),
    "Filmmakers" to listOf(
        m("mv-nolan", "Christopher Nolan", "Big ideas and spectacle", "Inception", "Interstellar", "Oppenheimer", "Memento"),
        m("mv-spielberg", "Steven Spielberg", "Adventure and drama", "Jaws", "Jurassic Park", "E.T.", "Saving Private Ryan"),
        m("mv-scorsese", "Martin Scorsese", "Crime and character", "Goodfellas", "Casino", "The Departed", "Taxi Driver"),
        m("mv-tarantino", "Quentin Tarantino", "Genre remix and cult favorites", "Pulp Fiction", "Kill Bill", "Django Unchained", "Inglourious Basterds"),
        m("mv-villeneuve", "Denis Villeneuve", "Atmospheric sci-fi and thrillers", "Arrival", "Dune", "Prisoners", "Sicario"),
        m("mv-fincher", "David Fincher", "Dark psychological precision", "Se7en", "Fight Club", "Gone Girl", "Zodiac"),
        m("mv-cameron", "James Cameron", "Blockbuster spectacle", "Titanic", "Aliens", "Terminator 2", "Avatar"),
        m("mv-coen", "Coen Brothers", "Crime, comedy and Americana", "Fargo", "No Country for Old Men", "The Big Lebowski", "True Grit"),
        m("mv-gerwig", "Greta Gerwig", "Character-rich modern favorites", "Lady Bird", "Little Women", "Barbie"),
        m("mv-peele", "Jordan Peele", "Social horror", "Get Out", "Us", "Nope"),
    ),
    "Actors" to listOf(
        m("mv-cruise", "Tom Cruise", "Action and star power", "Top Gun Maverick", "Mission Impossible", "Edge of Tomorrow", "Collateral"),
        m("mv-denzel", "Denzel Washington", "Thrillers and drama", "Training Day", "Man on Fire", "Inside Man", "The Equalizer"),
        m("mv-leo", "Leonardo DiCaprio", "Prestige and blockbusters", "Inception", "The Departed", "The Revenant", "Shutter Island"),
        m("mv-keanu", "Keanu Reeves", "Action and cult favorites", "John Wick", "The Matrix", "Speed", "Constantine"),
        m("mv-meryl", "Meryl Streep", "Acclaimed performances", "The Devil Wears Prada", "Doubt", "The Post", "Mamma Mia"),
        m("mv-hanks", "Tom Hanks", "Beloved modern classics", "Forrest Gump", "Cast Away", "Saving Private Ryan", "Catch Me If You Can"),
        m("mv-blanchett", "Cate Blanchett", "Prestige performances", "Tár", "Carol", "Blue Jasmine", "The Aviator"),
        m("mv-pitt", "Brad Pitt", "Drama, action and cult hits", "Fight Club", "Seven", "Moneyball", "Once Upon a Time in Hollywood"),
    ),
    "Studios, Awards & Specialty" to listOf(
        m("mv-pixar", "Pixar", "Animation favorites", "Toy Story", "Finding Nemo", "Coco", "Ratatouille"),
        m("mv-dreamworks", "DreamWorks", "Animated adventures", "Shrek", "Kung Fu Panda", "How to Train Your Dragon", "Madagascar"),
        m("mv-ghibli", "Studio Ghibli", "Hand-crafted classics", "Spirited Away", "Princess Mononoke", "Howl's Moving Castle", "My Neighbor Totoro"),
        m("mv-a24", "A24", "Distinctive modern indie cinema", "Hereditary", "Moonlight", "Uncut Gems", "Past Lives"),
        m("mv-bestpicture", "Best Picture Winners", "Academy Award winners", "Oppenheimer", "Parasite", "Moonlight", "The Shape of Water"),
        m("mv-oscar", "Oscar Performances", "Award-winning acting", "The Whale", "Joker", "Black Swan", "There Will Be Blood"),
        m("mv-cult", "Cult Classics", "Movies with devoted followings", "The Big Lebowski", "Donnie Darko", "Fight Club", "The Rocky Horror Picture Show"),
        m("mv-hidden", "Hidden Gems", "Excellent under-the-radar picks", "Coherence", "The Vast of Night", "Hunt for the Wilderpeople", "Upgrade"),
        m("mv-foreign", "International Favorites", "Great films from around the world", "Parasite", "The Handmaiden", "City of God", "Amélie"),
        m("mv-blackcinema", "Black Cinema", "Landmark and modern favorites", "Moonlight", "Do the Right Thing", "Get Out", "Creed"),
        m("mv-women-directors", "Women Directors", "Acclaimed films by women filmmakers", "Lady Bird", "The Hurt Locker", "Past Lives", "Nomadland"),
    ),
    "Seasonal" to listOf(
        m("mv-halloween", "Halloween Season", "Spooky-season favorites", "Halloween", "Hocus Pocus", "Scream", "The Conjuring"),
        m("mv-holiday", "Holiday Movies", "Holiday comfort viewing", "Home Alone", "Elf", "The Santa Clause", "The Polar Express"),
        m("mv-valentine", "Valentine's Day", "Romance for date night", "About Time", "The Notebook", "Crazy Rich Asians", "Before Sunrise"),
        m("mv-summer", "Summer Blockbusters", "Big-screen spectacle", "Jaws", "Jurassic Park", "Independence Day", "Top Gun Maverick"),
        m("mv-school", "Back to School", "School and coming-of-age", "The Breakfast Club", "Mean Girls", "Superbad", "Booksmart"),
    ),
)

private val tvRows: List<Pair<String, List<MassiveListSpec>>> = listOf(
    "Trending & Binge" to listOf(
        m("tv-hot", "Weekly Hot TV", "Series people are watching now", "The Last of Us", "House of the Dragon", "Severance", "The Bear"),
        m("tv-new", "New & Returning", "Current and returning favorites", "Wednesday", "Reacher", "The White Lotus", "Andor"),
        m("tv-binge", "Binge Worthy", "Easy shows to keep watching", "Breaking Bad", "Better Call Saul", "Ozark", "Succession"),
        m("tv-talked", "Most Talked About", "Big conversation series", "The Bear", "Severance", "Yellowjackets", "The Last of Us"),
        m("tv-comfort", "Comfort TV", "Easy rewatches", "Friends", "The Office", "Parks and Recreation", "Gilmore Girls"),
        m("tv-short", "Short Binge", "Limited and quick series", "Chernobyl", "Mare of Easttown", "Beef", "The Queen's Gambit"),
    ),
    "Genres" to listOf(
        m("tv-crime", "Crime & Mystery", "Detectives and dark mysteries", "True Detective", "Mindhunter", "Sherlock", "Mare of Easttown"),
        m("tv-comedy", "Comedy", "Modern and classic comedy", "The Office", "Parks and Recreation", "Ted Lasso", "Abbott Elementary"),
        m("tv-scifi", "Sci-Fi", "Big worlds and concepts", "The Expanse", "Black Mirror", "Foundation", "Silo"),
        m("tv-fantasy", "Fantasy", "Epic worlds and magic", "Game of Thrones", "House of the Dragon", "The Witcher", "Shadow and Bone"),
        m("tv-horror", "Horror", "Dark and unsettling series", "The Haunting of Hill House", "Yellowjackets", "From", "Midnight Mass"),
        m("tv-action", "Action", "High-stakes series", "Reacher", "Jack Ryan", "The Night Agent", "24"),
        m("tv-medical", "Medical", "Hospitals and hard choices", "ER", "Grey's Anatomy", "The Pitt", "The Good Doctor"),
        m("tv-legal", "Legal", "Courtrooms and power plays", "Suits", "The Good Wife", "Better Call Saul", "The Lincoln Lawyer"),
        m("tv-romance", "Romance", "Love and relationships", "Bridgerton", "Normal People", "Outlander", "Heartstopper"),
        m("tv-family", "Family", "Family-friendly series", "Bluey", "Percy Jackson", "Avatar The Last Airbender", "A Series of Unfortunate Events"),
        m("tv-western", "Westerns", "Modern frontier stories", "Yellowstone", "1883", "Deadwood", "Godless"),
        m("tv-politics", "Political Drama", "Power, policy and scandal", "The West Wing", "House of Cards", "Scandal", "The Diplomat"),
        m("tv-spy", "Spy & Espionage", "Secrets and intelligence work", "Slow Horses", "The Americans", "Homeland", "Killing Eve"),
        m("tv-superhero", "Superhero TV", "Comic-book series", "The Boys", "Daredevil", "Watchmen", "Peacemaker"),
    ),
    "Prestige & Limited" to listOf(
        m("tv-prestige", "Prestige Drama", "Premium character-driven series", "The Sopranos", "Succession", "Mad Men", "The Wire"),
        m("tv-limited", "Limited Series", "One-and-done stories", "Chernobyl", "Mare of Easttown", "The Queen's Gambit", "Sharp Objects"),
        m("tv-awards", "Award Winners", "Acclaimed television", "Succession", "The Bear", "Fleabag", "Breaking Bad"),
        m("tv-critics", "Critics' Favorites", "Highly acclaimed series", "Better Call Saul", "The Leftovers", "Severance", "Reservation Dogs"),
        m("tv-slowburn", "Slow-Burn Drama", "Patient, rewarding storytelling", "The Americans", "Rectify", "The Leftovers", "Halt and Catch Fire"),
        m("tv-dark", "Dark Drama", "Heavy and atmospheric series", "Ozark", "Sharp Objects", "True Detective", "The Night Of"),
    ),
    "Comedy & Comfort" to listOf(
        m("tv-workplace", "Workplace Comedy", "Office chaos and ensemble comedy", "The Office", "Parks and Recreation", "Superstore", "Abbott Elementary"),
        m("tv-sitcom", "Classic Sitcoms", "Iconic comedy series", "Friends", "Seinfeld", "Frasier", "Cheers"),
        m("tv-darkcomedy", "Dark Comedy", "Funny with an edge", "Barry", "Fleabag", "The White Lotus", "Atlanta"),
        m("tv-familycomedy", "Family Comedy", "Family-centered laughs", "Modern Family", "Black-ish", "Fresh Off the Boat", "The Middle"),
        m("tv-mockumentary", "Mockumentary", "Documentary-style comedy", "The Office", "Parks and Recreation", "Abbott Elementary", "What We Do in the Shadows"),
        m("tv-cozy", "Cozy TV", "Warm and easy viewing", "Gilmore Girls", "Ted Lasso", "Schitt's Creek", "Virgin River"),
    ),
    "Networks & Streaming" to listOf(
        m("tv-hbo", "HBO Favorites", "Prestige HBO series", "The Sopranos", "Succession", "The Last of Us", "Chernobyl"),
        m("tv-netflix", "Netflix Hits", "Streaming-era favorites", "Stranger Things", "Wednesday", "The Crown", "Ozark"),
        m("tv-apple", "Apple TV+", "Premium Apple originals", "Severance", "Ted Lasso", "Silo", "Slow Horses"),
        m("tv-hulu-fx", "Hulu & FX", "Distinctive modern series", "The Bear", "Shogun", "Atlanta", "Fargo"),
        m("tv-prime", "Prime Video", "Popular Prime series", "The Boys", "Reacher", "Fallout", "The Marvelous Mrs Maisel"),
        m("tv-paramount", "Paramount+", "Popular Paramount series", "Yellowstone", "Tulsa King", "1923", "Star Trek Strange New Worlds"),
        m("tv-peacock", "Peacock", "NBCUniversal favorites", "Poker Face", "Bel-Air", "The Resort", "Mrs Davis"),
        m("tv-disney", "Disney+", "Franchises and family series", "The Mandalorian", "Loki", "Andor", "Percy Jackson"),
    ),
    "Classic TV by Era" to listOf(
        m("tv-80s", "80s TV", "Iconic 1980s television", "Cheers", "Miami Vice", "The Golden Girls", "Magnum PI"),
        m("tv-90s", "90s TV", "Defining 1990s series", "Friends", "The X-Files", "Seinfeld", "Twin Peaks"),
        m("tv-2000s", "2000s TV", "Prestige and network classics", "Lost", "The Wire", "The Office", "House"),
        m("tv-2010s", "2010s TV", "Defining modern television", "Game of Thrones", "Breaking Bad", "Fleabag", "Mr Robot"),
        m("tv-2020s", "2020s So Far", "Current-decade standouts", "Severance", "The Bear", "The Last of Us", "Shogun"),
    ),
    "Reality, Docs & Competition" to listOf(
        m("tv-docuseries", "Docuseries", "True stories in episodes", "The Last Dance", "Wild Wild Country", "The Jinx", "Chef's Table"),
        m("tv-truecrime", "True Crime", "Real cases and investigations", "Making a Murderer", "The Staircase", "The Keepers", "Evil Genius"),
        m("tv-food", "Food & Cooking", "Chefs, travel and kitchens", "Chef's Table", "The Bear", "Somebody Feed Phil", "Salt Fat Acid Heat"),
        m("tv-competition", "Competition", "High-stakes reality contests", "Survivor", "The Amazing Race", "Top Chef", "The Traitors"),
        m("tv-home", "Home & Design", "Homes, renovation and design", "Queer Eye", "Dream Home Makeover", "Fixer Upper", "Grand Designs"),
        m("tv-travel", "Travel", "Explore the world", "Anthony Bourdain Parts Unknown", "Somebody Feed Phil", "Long Way Round", "Travel Man"),
    ),
    "Animation & International" to listOf(
        m("tv-adultanim", "Adult Animation", "Animated series for adults", "BoJack Horseman", "Rick and Morty", "Archer", "Invincible"),
        m("tv-anime", "Anime", "Popular anime series", "Attack on Titan", "Demon Slayer", "Jujutsu Kaisen", "Fullmetal Alchemist Brotherhood"),
        m("tv-kidsanim", "Kids Animation", "Family-friendly animation", "Bluey", "Avatar The Last Airbender", "Gravity Falls", "Adventure Time"),
        m("tv-kdrama", "K-Dramas", "Popular Korean dramas", "Crash Landing on You", "Squid Game", "Vincenzo", "Extraordinary Attorney Woo"),
        m("tv-british", "British TV", "UK favorites", "Sherlock", "Peaky Blinders", "Fleabag", "Slow Horses"),
        m("tv-nordic", "Nordic & European", "European crime and drama", "The Bridge", "Borgen", "Dark", "Babylon Berlin"),
        m("tv-spanish", "Spanish-Language", "Popular Spanish-language series", "Money Heist", "Elite", "Narcos", "Cable Girls"),
        m("tv-international", "International Hits", "Series from around the world", "Dark", "Squid Game", "Money Heist", "Lupin"),
    ),
    "Fan Favorites" to listOf(
        m("tv-cult", "Cult TV", "Devoted fan followings", "Twin Peaks", "Firefly", "Community", "Hannibal"),
        m("tv-rewatch", "Highly Rewatchable", "Shows worth returning to", "The Office", "Friends", "Breaking Bad", "Ted Lasso"),
        m("tv-mysterybox", "Mystery Box", "Big questions and theories", "Lost", "Dark", "Severance", "Yellowjackets"),
        m("tv-antihero", "Antiheroes", "Complicated leads", "Breaking Bad", "The Sopranos", "Barry", "Dexter"),
        m("tv-femaleled", "Female-Led Favorites", "Great series led by women", "Killing Eve", "Fleabag", "The Marvelous Mrs Maisel", "Big Little Lies"),
        m("tv-ensemble", "Great Ensembles", "Strong casts and chemistry", "Succession", "Community", "The Bear", "Lost"),
    ),
)

@Composable
fun ExpandedMovieListsScreen() = MassiveListsScreen(
    pageTitle = "Movies",
    pageSubtitle = "100+ visual collections across franchises, genres, moods, decades, filmmakers, stars, awards and seasonal picks.",
    rows = movieRows,
    mediaType = LibraryMediaType.MOVIE,
)

@Composable
fun ExpandedTvListsScreen() = MassiveListsScreen(
    pageTitle = "TV Shows",
    pageSubtitle = "70+ binge-ready collections across genres, prestige, comedy, networks, eras, reality, animation and international TV.",
    rows = tvRows,
    mediaType = LibraryMediaType.SERIES,
)

@Composable
private fun MassiveListsScreen(
    pageTitle: String,
    pageSubtitle: String,
    rows: List<Pair<String, List<MassiveListSpec>>>,
    mediaType: LibraryMediaType,
) {
    val context = LocalContext.current
    val metadata = remember { AstraWaveMetadataGateway() }
    val states = remember { mutableStateMapOf<String, MassiveListState>() }
    var selected by remember { mutableStateOf<MassiveListSpec?>(null) }

    LaunchedEffect(rows) {
        rows.flatMap { it.second }.forEach { spec ->
            if (states.containsKey(spec.id)) return@forEach
            states[spec.id] = MassiveListState.Loading
            states[spec.id] = try {
                val items = withContext(Dispatchers.IO) {
                    spec.queries.flatMap { metadata.search(it) }
                        .filter { item ->
                            if (mediaType == LibraryMediaType.MOVIE) item.type.equals("movie", true)
                            else item.type.equals("series", true) || item.type.equals("tv", true)
                        }
                        .distinctBy { it.id }
                        .take(60)
                        .onEach { ArtworkRegistry.register(it.name, it.posterUrl ?: it.backdropUrl) }
                }
                MassiveListState.Ready(items)
            } catch (error: Exception) {
                MassiveListState.Error(error.message ?: "Unable to load collection")
            }
        }
    }

    selected?.let { spec ->
        MassiveListDetail(
            spec = spec,
            state = states[spec.id] ?: MassiveListState.Loading,
            mediaType = mediaType,
            onBack = { selected = null },
            onOpen = { item ->
                val sourceId = when {
                    item.id.startsWith("tt", true) -> "stremio:cinemeta:${if (mediaType == LibraryMediaType.SERIES) "series" else "movie"}:${item.id}"
                    else -> "tmdb:${item.id}"
                }
                context.startActivity(
                    Intent(context, TitleDetailsActivity::class.java)
                        .putExtra(TitleDetailsActivity.EXTRA_TITLE, item.name)
                        .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, mediaType.name)
                        .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, sourceId),
                )
            },
        )
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        AstraWavePageHeader(title = pageTitle, subtitle = pageSubtitle)
        Spacer(Modifier.height(18.dp))
        rows.forEach { (rowTitle, specs) ->
            Text(rowTitle, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                specs.forEach { spec ->
                    val items = (states[spec.id] as? MassiveListState.Ready)?.items.orEmpty()
                    AstraWaveFocusableCard(Modifier.width(248.dp).clickable { selected = spec }) {
                        Column {
                            Box(Modifier.fillMaxWidth().height(138.dp)) {
                                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    val coverItems = if (items.isEmpty()) listOf<AstraWaveMetadataGateway.Item?>(null, null, null)
                                    else List(3) { i -> items.getOrNull(i % items.size) }
                                    coverItems.forEach { item ->
                                        Box(Modifier.weight(1f).fillMaxHeight()) {
                                            AstraWaveArtwork(title = item?.name ?: spec.title, modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                }
                                Text(
                                    if (states[spec.id] is MassiveListState.Loading) "…" else items.size.toString(),
                                    color = AstraWaveColors.PrimaryText,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                        .background(AstraWaveColors.Background.copy(alpha = 0.82f), RoundedCornerShape(8.dp))
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
            }
            Spacer(Modifier.height(22.dp))
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun MassiveListDetail(
    spec: MassiveListSpec,
    state: MassiveListState,
    mediaType: LibraryMediaType,
    onBack: () -> Unit,
    onOpen: (AstraWaveMetadataGateway.Item) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        AstraWavePageHeader(title = spec.title, subtitle = spec.subtitle)
        Spacer(Modifier.height(10.dp))
        AstraWaveSecondaryButton(label = "← Back to Lists", onClick = onBack)
        Spacer(Modifier.height(18.dp))
        when (state) {
            MassiveListState.Loading -> AstraWaveStatePanel("Loading ${spec.title}…", "Refreshing this collection.", loading = true)
            is MassiveListState.Error -> AstraWaveStatePanel("Collection unavailable", state.message)
            is MassiveListState.Ready -> {
                Text(
                    "${state.items.size} ${if (mediaType == LibraryMediaType.MOVIE) "movies" else "series"}",
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(12.dp))
                state.items.forEach { item ->
                    AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onOpen(item) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(118.dp)) { AstraWaveArtwork(title = item.name, modifier = Modifier.fillMaxWidth()) }
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    item.description ?: "Open for details and watch options.",
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
