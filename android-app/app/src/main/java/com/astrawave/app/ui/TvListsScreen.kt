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
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.data.AstraWaveMetadataGateway
import com.astrawave.app.data.ArtworkRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class TvCollection(val id: String, val title: String, val subtitle: String, val queries: List<String>)

private sealed interface TvCollectionState {
    data object Loading : TvCollectionState
    data class Ready(val items: List<AstraWaveMetadataGateway.Item>) : TvCollectionState
    data class Error(val message: String) : TvCollectionState
}

private val tvHot = listOf(
    TvCollection("tv-trending", "Weekly Hot TV", "Series people are watching now", listOf("The Last of Us", "House of the Dragon", "Severance", "The Bear", "Reacher")),
    TvCollection("tv-new", "New & Returning", "Current shows and returning favorites", listOf("Wednesday", "The White Lotus", "Andor", "Yellowjackets", "The Night Agent")),
    TvCollection("tv-binge", "Binge Worthy", "Easy shows to keep watching", listOf("Breaking Bad", "Better Call Saul", "Ozark", "Succession", "Peaky Blinders")),
    TvCollection("tv-most-talked", "Most Talked About", "Buzz-heavy shows and cultural hits", listOf("Euphoria", "The Boys", "Squid Game", "Yellowstone", "Bridgerton")),
)

private val tvGenres = listOf(
    TvCollection("tv-crime", "Crime & Mystery", "Detectives, thrillers and dark mysteries", listOf("True Detective", "Mindhunter", "Sherlock", "Mare of Easttown", "Broadchurch")),
    TvCollection("tv-comedy", "Comedy Favorites", "Comfort comedies and modern hits", listOf("The Office", "Parks and Recreation", "Ted Lasso", "Abbott Elementary", "Brooklyn Nine-Nine")),
    TvCollection("tv-sci-fi", "Sci-Fi TV", "Big worlds and high-concept series", listOf("Stranger Things", "The Expanse", "Black Mirror", "Foundation", "Silo")),
    TvCollection("tv-fantasy", "Fantasy Worlds", "Magic, kingdoms and supernatural worlds", listOf("Game of Thrones", "House of the Dragon", "The Witcher", "Shadow and Bone", "His Dark Materials")),
    TvCollection("tv-horror", "Horror & Supernatural", "Dark series, monsters and scares", listOf("The Haunting of Hill House", "Midnight Mass", "American Horror Story", "From", "Penny Dreadful")),
    TvCollection("tv-action", "Action & Adventure", "Fast-moving shows with big stakes", listOf("Reacher", "Jack Ryan", "The Terminal List", "24", "Banshee")),
    TvCollection("tv-medical", "Medical Drama", "Hospitals, doctors and high-pressure cases", listOf("Grey's Anatomy", "House", "The Good Doctor", "ER", "The Pitt")),
    TvCollection("tv-legal", "Legal & Courtroom", "Lawyers, cases and courtroom drama", listOf("Suits", "The Good Wife", "Better Call Saul", "The Lincoln Lawyer", "Boston Legal")),
    TvCollection("tv-family", "Family TV", "Shows that work for family viewing", listOf("Avatar The Last Airbender", "Bluey", "Percy Jackson", "A Series of Unfortunate Events", "The Muppets")),
    TvCollection("tv-romance", "Romance & Relationships", "Romantic drama and relationship stories", listOf("Bridgerton", "Normal People", "Outlander", "Heartstopper", "Virgin River")),
)

private val tvPrestige = listOf(
    TvCollection("tv-hbo", "Prestige Drama", "Landmark premium drama", listOf("The Sopranos", "Succession", "The Wire", "Six Feet Under", "The Leftovers")),
    TvCollection("tv-limited", "Limited Series", "Short-form prestige stories", listOf("Chernobyl", "Mare of Easttown", "The Queen's Gambit", "Unbelievable", "When They See Us")),
    TvCollection("tv-awards", "Award Winners", "Acclaimed and decorated television", listOf("Breaking Bad", "Succession", "The Crown", "Fleabag", "Mad Men")),
    TvCollection("tv-critics", "Critics' Favorites", "Highly acclaimed modern television", listOf("Severance", "The Bear", "Better Call Saul", "Atlanta", "Reservation Dogs")),
)

private val tvStreaming = listOf(
    TvCollection("tv-netflix", "Netflix-Era Hits", "Popular streaming-era series", listOf("Stranger Things", "Wednesday", "The Crown", "Ozark", "Squid Game")),
    TvCollection("tv-apple", "Apple TV+ Favorites", "Prestige sci-fi, comedy and drama", listOf("Ted Lasso", "Severance", "Silo", "For All Mankind", "Slow Horses")),
    TvCollection("tv-hulu", "Hulu & FX Favorites", "Acclaimed comedy, drama and thrillers", listOf("The Bear", "Shogun", "Only Murders in the Building", "Fargo", "The Handmaid's Tale")),
    TvCollection("tv-prime", "Prime Favorites", "Action, fantasy and streaming hits", listOf("The Boys", "Reacher", "Fallout", "The Marvelous Mrs Maisel", "The Expanse")),
    TvCollection("tv-paramount", "Paramount+ Favorites", "Drama, sci-fi and franchise series", listOf("Yellowstone", "1923", "Star Trek Strange New Worlds", "Tulsa King", "Mayor of Kingstown")),
)

private val tvClassics = listOf(
    TvCollection("tv-90s", "90s TV Classics", "Defining series from the 1990s", listOf("Friends", "The X-Files", "Seinfeld", "Frasier", "ER")),
    TvCollection("tv-2000s", "2000s Favorites", "Big hits from the 2000s", listOf("Lost", "House", "The Office", "Dexter", "24")),
    TvCollection("tv-2010s", "2010s Favorites", "Peak-TV era essentials", listOf("Game of Thrones", "Breaking Bad", "Mad Men", "The Walking Dead", "Black Mirror")),
    TvCollection("tv-cult", "Cult TV", "Series with devoted fan followings", listOf("Firefly", "Twin Peaks", "Community", "Arrested Development", "Freaks and Geeks")),
)

private val tvSitcoms = listOf(
    TvCollection("tv-workplace", "Workplace Comedies", "Offices, crews and found families", listOf("The Office", "Parks and Recreation", "Brooklyn Nine-Nine", "Superstore", "Abbott Elementary")),
    TvCollection("tv-comfort", "Comfort TV", "Familiar shows for easy watching", listOf("Friends", "Gilmore Girls", "New Girl", "Schitt's Creek", "Modern Family")),
    TvCollection("tv-dark-comedy", "Dark Comedy", "Sharp, strange and subversive comedy", listOf("Barry", "Fleabag", "Atlanta", "The White Lotus", "Beef")),
)

private val tvRealityDoc = listOf(
    TvCollection("tv-docuseries", "Docuseries", "True stories and deep dives", listOf("Making a Murderer", "The Last Dance", "Wild Wild Country", "Chef's Table", "Planet Earth")),
    TvCollection("tv-true-crime", "True Crime", "Investigations and real cases", listOf("The Jinx", "Tiger King", "Night Stalker", "The Staircase", "Evil Genius")),
    TvCollection("tv-food", "Food & Cooking", "Cooking, restaurants and competition", listOf("Chef's Table", "Top Chef", "The Great British Bake Off", "MasterChef", "Somebody Feed Phil")),
    TvCollection("tv-competition", "Competition TV", "Big challenges and reality competition", listOf("Survivor", "The Amazing Race", "The Traitors", "RuPaul's Drag Race", "The Challenge")),
)

private val tvAnimation = listOf(
    TvCollection("tv-adult-animation", "Adult Animation", "Animated comedy and genre favorites", listOf("Rick and Morty", "BoJack Horseman", "South Park", "Futurama", "Archer")),
    TvCollection("tv-anime", "Anime Essentials", "Popular anime series and gateways", listOf("Attack on Titan", "Death Note", "Demon Slayer", "Jujutsu Kaisen", "Fullmetal Alchemist Brotherhood")),
    TvCollection("tv-kids-animation", "Kids Animation", "Family-friendly animated favorites", listOf("Bluey", "Avatar The Last Airbender", "SpongeBob SquarePants", "Gravity Falls", "Adventure Time")),
)

private val tvInternational = listOf(
    TvCollection("tv-korean", "K-Drama Hits", "Popular Korean drama and thrillers", listOf("Squid Game", "Crash Landing on You", "Extraordinary Attorney Woo", "Vincenzo", "Moving")),
    TvCollection("tv-british", "British TV", "Crime, comedy and prestige drama", listOf("Sherlock", "Peaky Blinders", "Broadchurch", "Fleabag", "The Crown")),
    TvCollection("tv-world", "World TV", "International breakout series", listOf("Dark", "Money Heist", "Lupin", "Gomorrah", "Babylon Berlin")),
)

@Composable
fun TvListsScreen() {
    val context = LocalContext.current
    val metadata = remember { AstraWaveMetadataGateway() }
    val specs = remember { tvHot + tvGenres + tvPrestige + tvStreaming + tvClassics + tvSitcoms + tvRealityDoc + tvAnimation + tvInternational }
    val states = remember { mutableStateMapOf<String, TvCollectionState>() }
    var selected by remember { mutableStateOf<TvCollection?>(null) }
    var mode by remember { mutableStateOf("Lists") }
    var showMore by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        specs.forEach { spec ->
            states[spec.id] = TvCollectionState.Loading
            states[spec.id] = try {
                val items = withContext(Dispatchers.IO) {
                    spec.queries.flatMap { metadata.search(it) }
                        .filter { it.type.equals("series", true) || it.type.equals("tv", true) }
                        .distinctBy { it.id }
                        .take(60)
                        .onEach { ArtworkRegistry.register(it.name, it.posterUrl ?: it.backdropUrl) }
                }
                TvCollectionState.Ready(items)
            } catch (error: Exception) {
                TvCollectionState.Error(error.message ?: "Unable to load TV collection")
            }
        }
    }

    selected?.let { spec ->
        TvCollectionDetail(spec, states[spec.id] ?: TvCollectionState.Loading, onBack = { selected = null }) { item ->
            val sourceId = if (item.id.startsWith("tt", true)) "stremio:cinemeta:series:${item.id}" else "tmdb:${item.id}"
            context.startActivity(Intent(context, TitleDetailsActivity::class.java)
                .putExtra(TitleDetailsActivity.EXTRA_TITLE, item.name)
                .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, LibraryMediaType.SERIES.name)
                .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, sourceId))
        }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(AstraWaveColors.Background).padding(horizontal = 24.dp, vertical = 20.dp)) {
        AstraWavePageHeader(title = "TV Shows", subtitle = "A huge visual collection wall for trending TV, genres, streaming hits, classics, sitcoms, reality, animation and international series.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == "Lists", onClick = { mode = "Lists" }, label = { Text("Lists") })
            FilterChip(selected = mode == "Browse", onClick = { mode = "Browse" }, label = { Text("Browse") })
        }
        Spacer(Modifier.height(18.dp))

        if (mode == "Browse") {
            TvCollectionRow("Trending TV", tvHot, states) { selected = it }
            Spacer(Modifier.height(22.dp))
            TvCollectionRow("Binge Picks", listOf(tvHot[2], tvGenres[0], tvPrestige[0]), states) { selected = it }
            return@Column
        }

        TvCollectionRow("Weekly Hot List", tvHot, states) { selected = it }
        Spacer(Modifier.height(22.dp))
        TvCollectionRow("Genres & Moods", tvGenres.take(if (showMore) tvGenres.size else 6), states, showMore = !showMore, onMore = { showMore = true }) { selected = it }
        Spacer(Modifier.height(22.dp))
        TvCollectionRow("Prestige & Awards", tvPrestige, states) { selected = it }
        Spacer(Modifier.height(22.dp))
        TvCollectionRow("Streaming Favorites", tvStreaming, states) { selected = it }
        Spacer(Modifier.height(22.dp))
        TvCollectionRow("Classics by Era", tvClassics, states) { selected = it }
        Spacer(Modifier.height(22.dp))

        if (showMore) {
            TvCollectionRow("Sitcoms & Comfort TV", tvSitcoms, states) { selected = it }
            Spacer(Modifier.height(22.dp))
            TvCollectionRow("Reality & Docuseries", tvRealityDoc, states) { selected = it }
            Spacer(Modifier.height(22.dp))
            TvCollectionRow("Animation & Anime", tvAnimation, states) { selected = it }
            Spacer(Modifier.height(22.dp))
            TvCollectionRow("International TV", tvInternational, states) { selected = it }
            Spacer(Modifier.height(22.dp))
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun TvCollectionRow(title: String, specs: List<TvCollection>, states: Map<String, TvCollectionState>, showMore: Boolean = false, onMore: () -> Unit = {}, onSelect: (TvCollection) -> Unit) {
    Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        specs.forEach { spec ->
            val items = (states[spec.id] as? TvCollectionState.Ready)?.items.orEmpty()
            AstraWaveFocusableCard(Modifier.width(250.dp).clickable { onSelect(spec) }) {
                Column {
                    Box(Modifier.fillMaxWidth().height(140.dp)) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            val covers = if (items.isEmpty()) listOf<AstraWaveMetadataGateway.Item?>(null, null, null) else List(3) { i -> items.getOrNull(i % items.size) }
                            covers.forEach { item -> Box(Modifier.weight(1f).fillMaxHeight()) { AstraWaveArtwork(title = item?.name ?: spec.title, modifier = Modifier.fillMaxSize()) } }
                        }
                        Text(if (items.isEmpty()) "…" else items.size.toString(), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(AstraWaveColors.Background.copy(alpha = 0.82f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
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
                Column(Modifier.fillMaxWidth().height(165.dp).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("＋", color = AstraWaveColors.Accent, style = MaterialTheme.typography.displaySmall)
                    Text("MORE", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.labelLarge)
                    Text("Open all TV collections", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TvCollectionDetail(spec: TvCollection, state: TvCollectionState, onBack: () -> Unit, onOpen: (AstraWaveMetadataGateway.Item) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(AstraWaveColors.Background).padding(horizontal = 24.dp, vertical = 20.dp)) {
        AstraWavePageHeader(title = spec.title, subtitle = spec.subtitle)
        Spacer(Modifier.height(10.dp))
        AstraWaveSecondaryButton(label = "← Back to Lists", onClick = onBack)
        Spacer(Modifier.height(18.dp))
        when (state) {
            TvCollectionState.Loading -> AstraWaveStatePanel("Loading ${spec.title}…", "Refreshing this TV collection.", loading = true)
            is TvCollectionState.Error -> AstraWaveStatePanel("Collection unavailable", state.message)
            is TvCollectionState.Ready -> {
                Text("${state.items.size} series", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))
                state.items.forEach { item ->
                    AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onOpen(item) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(118.dp)) { AstraWaveArtwork(title = item.name, modifier = Modifier.fillMaxWidth()) }
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(5.dp))
                                Text(item.description ?: "Open for series details, episodes and watch options.", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}
