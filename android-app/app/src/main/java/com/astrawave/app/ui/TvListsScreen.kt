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

private data class TvCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    val queries: List<String>,
)

private sealed interface TvCollectionState {
    data object Loading : TvCollectionState
    data class Ready(val items: List<AstraWaveMetadataGateway.Item>) : TvCollectionState
    data class Error(val message: String) : TvCollectionState
}

private val tvHot = listOf(
    TvCollection("tv-trending", "Weekly Hot TV", "Series people are watching now", listOf("The Last of Us", "House of the Dragon", "Severance", "The Bear")),
    TvCollection("tv-new", "New & Returning", "Current shows and returning favorites", listOf("Wednesday", "Reacher", "The White Lotus", "Andor")),
    TvCollection("tv-binge", "Binge Worthy", "Easy shows to keep watching", listOf("Breaking Bad", "Better Call Saul", "Ozark", "Succession")),
)

private val tvGenres = listOf(
    TvCollection("tv-crime", "Crime & Mystery", "Detectives, thrillers and dark mysteries", listOf("True Detective", "Mindhunter", "Sherlock", "Mare of Easttown")),
    TvCollection("tv-comedy", "Comedy Favorites", "Comfort comedies and modern hits", listOf("The Office", "Parks and Recreation", "Ted Lasso", "Abbott Elementary")),
    TvCollection("tv-sci-fi", "Sci-Fi TV", "Big worlds and high-concept series", listOf("Stranger Things", "The Expanse", "Black Mirror", "Foundation")),
    TvCollection("tv-family", "Family TV", "Shows that work for family viewing", listOf("Avatar The Last Airbender", "Bluey", "Percy Jackson", "A Series of Unfortunate Events")),
)

private val tvNetworks = listOf(
    TvCollection("tv-hbo", "Prestige Drama", "Premium drama and limited series", listOf("The Sopranos", "Succession", "Chernobyl", "The Last of Us")),
    TvCollection("tv-netflix", "Streaming Hits", "Popular streaming-era series", listOf("Stranger Things", "Wednesday", "The Crown", "Ozark")),
    TvCollection("tv-classics", "TV Classics", "Long-running favorites and landmark series", listOf("Friends", "The X-Files", "Lost", "The Wire")),
)

@Composable
fun TvListsScreen() {
    val context = LocalContext.current
    val metadata = remember { AstraWaveMetadataGateway() }
    val specs = remember { tvHot + tvGenres + tvNetworks }
    val states = remember { mutableStateMapOf<String, TvCollectionState>() }
    var selected by remember { mutableStateOf<TvCollection?>(null) }
    var mode by remember { mutableStateOf("Lists") }

    LaunchedEffect(Unit) {
        specs.forEach { spec ->
            states[spec.id] = TvCollectionState.Loading
            states[spec.id] = try {
                val items = withContext(Dispatchers.IO) {
                    spec.queries.flatMap { metadata.search(it) }
                        .filter { it.type.equals("series", true) || it.type.equals("tv", true) }
                        .distinctBy { it.id }
                        .take(50)
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
            context.startActivity(
                Intent(context, TitleDetailsActivity::class.java)
                    .putExtra(TitleDetailsActivity.EXTRA_TITLE, item.name)
                    .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, LibraryMediaType.SERIES.name)
                    .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, sourceId),
            )
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(AstraWaveColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        AstraWavePageHeader(
            title = "TV Shows",
            subtitle = "MovieBoxPro-style visual TV collections with binge lists, genres, networks and classics.",
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == "Lists", onClick = { mode = "Lists" }, label = { Text("Lists") })
            FilterChip(selected = mode == "Browse", onClick = { mode = "Browse" }, label = { Text("Browse") })
        }
        Spacer(Modifier.height(18.dp))

        if (mode == "Browse") {
            TvCollectionRow("Trending TV", tvHot.take(2), states) { selected = it }
            Spacer(Modifier.height(22.dp))
            TvCollectionRow("Binge Picks", listOf(tvHot.last(), tvGenres.first()), states) { selected = it }
            return@Column
        }

        TvCollectionRow("Weekly Hot List", tvHot, states) { selected = it }
        Spacer(Modifier.height(22.dp))
        TvCollectionRow("Genres & Moods", tvGenres, states) { selected = it }
        Spacer(Modifier.height(22.dp))
        TvCollectionRow("Networks & Classics", tvNetworks, states) { selected = it }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun TvCollectionRow(
    title: String,
    specs: List<TvCollection>,
    states: Map<String, TvCollectionState>,
    onSelect: (TvCollection) -> Unit,
) {
    Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        specs.forEach { spec ->
            val items = (states[spec.id] as? TvCollectionState.Ready)?.items.orEmpty()
            AstraWaveFocusableCard(Modifier.width(250.dp).clickable { onSelect(spec) }) {
                Column {
                    Box(Modifier.fillMaxWidth().height(140.dp)) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            val covers = if (items.isEmpty()) listOf<AstraWaveMetadataGateway.Item?>(null, null, null) else List(3) { index -> items.getOrNull(index % items.size) }
                            covers.forEach { item ->
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    AstraWaveArtwork(title = item?.name ?: spec.title, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                        Text(
                            if (items.isEmpty()) "…" else items.size.toString(),
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
}

@Composable
private fun TvCollectionDetail(
    spec: TvCollection,
    state: TvCollectionState,
    onBack: () -> Unit,
    onOpen: (AstraWaveMetadataGateway.Item) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(AstraWaveColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
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
