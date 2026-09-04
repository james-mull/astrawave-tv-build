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
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.data.ArtworkRegistry
import com.astrawave.app.data.AstraWaveMetadataGateway
import com.astrawave.app.data.DynamicCollectionRepository
import com.astrawave.app.data.PersonalizedCollectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class DynamicListSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val genre: String? = null,
    val pages: Int = 3,
)

private sealed interface DynamicListState {
    data object Loading : DynamicListState
    data class Ready(val items: List<AstraWaveMetadataGateway.Item>) : DynamicListState
    data class Error(val message: String) : DynamicListState
}

private val dynamicMovieRows = listOf(
    "Updated Automatically" to listOf(
        DynamicListSpec("movie-top100", "Top 100 Right Now", "Live ranking that refreshes automatically", pages = 5),
        DynamicListSpec("movie-action", "Popular Action", "Current action favorites", "Action", 3),
        DynamicListSpec("movie-comedy", "Comedy Right Now", "Popular comedy picks", "Comedy", 3),
        DynamicListSpec("movie-drama", "Popular Drama", "Current drama favorites", "Drama", 3),
    ),
    "Genre Charts" to listOf(
        DynamicListSpec("movie-horror", "Horror Right Now", "Current horror chart", "Horror", 3),
        DynamicListSpec("movie-scifi", "Sci-Fi Right Now", "Popular science-fiction", "Sci-Fi", 3),
        DynamicListSpec("movie-thriller", "Thrillers Right Now", "Popular thrillers", "Thriller", 3),
        DynamicListSpec("movie-romance", "Romance Right Now", "Popular romance titles", "Romance", 3),
        DynamicListSpec("movie-family", "Family Favorites Now", "Current family viewing", "Family", 3),
        DynamicListSpec("movie-animation", "Animation Right Now", "Current animated favorites", "Animation", 3),
        DynamicListSpec("movie-fantasy", "Fantasy Right Now", "Popular fantasy adventures", "Fantasy", 3),
        DynamicListSpec("movie-crime", "Crime Right Now", "Popular crime movies", "Crime", 3),
    ),
)

private val dynamicTvRows = listOf(
    "Updated Automatically" to listOf(
        DynamicListSpec("tv-top100", "Top 100 TV Right Now", "Live series ranking that refreshes automatically", pages = 5),
        DynamicListSpec("tv-drama", "Popular Drama", "Current drama series", "Drama", 3),
        DynamicListSpec("tv-comedy", "Comedy Right Now", "Current comedy series", "Comedy", 3),
        DynamicListSpec("tv-action", "Action & Adventure", "Current action series", "Action", 3),
    ),
    "Genre Charts" to listOf(
        DynamicListSpec("tv-crime", "Crime & Mystery", "Current crime and mystery series", "Crime", 3),
        DynamicListSpec("tv-scifi", "Sci-Fi Right Now", "Popular science-fiction series", "Sci-Fi", 3),
        DynamicListSpec("tv-fantasy", "Fantasy Right Now", "Popular fantasy series", "Fantasy", 3),
        DynamicListSpec("tv-horror", "Horror Right Now", "Current horror series", "Horror", 3),
        DynamicListSpec("tv-family", "Family TV Right Now", "Current family series", "Family", 3),
        DynamicListSpec("tv-animation", "Animation Right Now", "Current animated series", "Animation", 3),
        DynamicListSpec("tv-romance", "Romance Right Now", "Current romance series", "Romance", 3),
        DynamicListSpec("tv-documentary", "Documentary Series", "Current documentary viewing", "Documentary", 3),
    ),
)

@Composable
fun DynamicMovieListsScreen(profileId: String = "default") = DynamicListsHub(
    title = "Movies",
    subtitle = "Personalized shelves, live Top 100 and genre charts, plus AstraWave's 100+ curated collections.",
    profileId = profileId,
    media = DynamicCollectionRepository.Media.MOVIE,
    mediaType = LibraryMediaType.MOVIE,
    rows = dynamicMovieRows,
    fullLibrary = { ExpandedMovieListsScreen() },
)

@Composable
fun DynamicTvListsScreen(profileId: String = "default") = DynamicListsHub(
    title = "TV Shows",
    subtitle = "Personalized binge shelves, live TV charts and genre lists, plus AstraWave's 70+ curated collections.",
    profileId = profileId,
    media = DynamicCollectionRepository.Media.SERIES,
    mediaType = LibraryMediaType.SERIES,
    rows = dynamicTvRows,
    fullLibrary = { ExpandedTvListsScreen() },
)

@Composable
private fun DynamicListsHub(
    title: String,
    subtitle: String,
    profileId: String,
    media: DynamicCollectionRepository.Media,
    mediaType: LibraryMediaType,
    rows: List<Pair<String, List<DynamicListSpec>>>,
    fullLibrary: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { DynamicCollectionRepository() }
    val personalizer = remember { PersonalizedCollectionRepository(context) }
    val states = remember { mutableStateMapOf<String, DynamicListState>() }
    var personalized by remember(profileId, media) { mutableStateOf<List<PersonalizedCollectionRepository.Shelf>>(emptyList()) }
    var personalizedLoading by remember(profileId, media) { mutableStateOf(true) }
    var selected by remember { mutableStateOf<DynamicListSpec?>(null) }
    var selectedPersonal by remember { mutableStateOf<PersonalizedCollectionRepository.Shelf?>(null) }
    var mode by remember { mutableStateOf("Live Lists") }

    if (mode == "All Collections") {
        Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = false, onClick = { mode = "Live Lists" }, label = { Text("Live Lists") })
                FilterChip(selected = true, onClick = {}, label = { Text("All Collections") })
            }
            Box(Modifier.weight(1f)) { fullLibrary() }
        }
        return
    }

    LaunchedEffect(profileId, media) {
        personalizedLoading = true
        personalized = withContext(Dispatchers.IO) {
            runCatching { personalizer.shelves(profileId, media) }.getOrDefault(emptyList())
        }
        personalized.forEach { shelf ->
            shelf.items.forEach { ArtworkRegistry.register(it.name, it.posterUrl ?: it.backdropUrl) }
        }
        personalizedLoading = false
    }

    LaunchedEffect(rows, media) {
        rows.flatMap { it.second }.forEach { spec ->
            if (states.containsKey(spec.id)) return@forEach
            states[spec.id] = DynamicListState.Loading
            states[spec.id] = try {
                val items = withContext(Dispatchers.IO) {
                    val loaded = if (spec.genre.isNullOrBlank()) repository.top(media, spec.pages)
                    else repository.genre(media, spec.genre, spec.pages)
                    loaded.distinctBy { it.id }.take(100).onEach {
                        ArtworkRegistry.register(it.name, it.posterUrl ?: it.backdropUrl)
                    }
                }
                DynamicListState.Ready(items)
            } catch (error: Exception) {
                DynamicListState.Error(error.message ?: "Unable to refresh this list")
            }
        }
    }

    selectedPersonal?.let { shelf ->
        PersonalizedListDetail(
            shelf = shelf,
            mediaType = mediaType,
            onBack = { selectedPersonal = null },
            onOpen = { openTitle(context, it, mediaType) },
        )
        return
    }

    selected?.let { spec ->
        DynamicListDetail(
            spec = spec,
            state = states[spec.id] ?: DynamicListState.Loading,
            mediaType = mediaType,
            onBack = { selected = null },
            onOpen = { openTitle(context, it, mediaType) },
        )
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        AstraWavePageHeader(title = title, subtitle = subtitle)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = true, onClick = {}, label = { Text("Live Lists") })
            FilterChip(selected = false, onClick = { mode = "All Collections" }, label = { Text("All Collections") })
        }
        Spacer(Modifier.height(18.dp))

        if (personalizedLoading) {
            AstraWaveStatePanel(
                "Personalizing your lists…",
                "Using this profile's watch history, favorites and saved titles.",
                loading = true,
            )
            Spacer(Modifier.height(22.dp))
        } else if (personalized.isNotEmpty()) {
            Text("Made For You", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Built from your recent viewing, favorites, watchlist and inferred genres",
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            PersonalizedShelfCards(personalized) { selectedPersonal = it }
            Spacer(Modifier.height(26.dp))
        }

        rows.forEach { (rowTitle, specs) ->
            Text(rowTitle, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Refreshed automatically from live metadata • cached briefly for faster browsing",
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                specs.forEach { spec ->
                    val items = (states[spec.id] as? DynamicListState.Ready)?.items.orEmpty()
                    CollectionCard(
                        title = spec.title,
                        subtitle = spec.subtitle,
                        items = items,
                        loading = states[spec.id] is DynamicListState.Loading,
                        onClick = { selected = spec },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PersonalizedShelfCards(
    shelves: List<PersonalizedCollectionRepository.Shelf>,
    onSelect: (PersonalizedCollectionRepository.Shelf) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        shelves.forEach { shelf ->
            CollectionCard(
                title = shelf.title,
                subtitle = shelf.subtitle,
                items = shelf.items,
                loading = false,
                onClick = { onSelect(shelf) },
            )
        }
    }
}

@Composable
private fun CollectionCard(
    title: String,
    subtitle: String,
    items: List<AstraWaveMetadataGateway.Item>,
    loading: Boolean,
    onClick: () -> Unit,
) {
    AstraWaveFocusableCard(Modifier.width(250.dp).clickable(onClick = onClick)) {
        Column {
            Box(Modifier.fillMaxWidth().height(140.dp)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val covers = if (items.isEmpty()) listOf<AstraWaveMetadataGateway.Item?>(null, null, null)
                    else List(3) { index -> items.getOrNull(index % items.size) }
                    covers.forEach { item ->
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            AstraWaveArtwork(title = item?.name ?: title, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                Text(
                    if (loading) "…" else items.size.toString(),
                    color = AstraWaveColors.PrimaryText,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(AstraWaveColors.Background.copy(alpha = 0.82f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}

private fun openTitle(
    context: android.content.Context,
    item: AstraWaveMetadataGateway.Item,
    mediaType: LibraryMediaType,
) {
    val sourceId = if (item.id.startsWith("tt", true)) {
        "stremio:cinemeta:${if (mediaType == LibraryMediaType.MOVIE) "movie" else "series"}:${item.id}"
    } else {
        "tmdb:${item.id}"
    }
    context.startActivity(
        Intent(context, TitleDetailsActivity::class.java)
            .putExtra(TitleDetailsActivity.EXTRA_TITLE, item.name)
            .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, mediaType.name)
            .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, sourceId),
    )
}

@Composable
private fun PersonalizedListDetail(
    shelf: PersonalizedCollectionRepository.Shelf,
    mediaType: LibraryMediaType,
    onBack: () -> Unit,
    onOpen: (AstraWaveMetadataGateway.Item) -> Unit,
) {
    CollectionDetailBody(
        title = shelf.title,
        subtitle = shelf.subtitle,
        items = shelf.items,
        mediaType = mediaType,
        status = "Personalized for this profile",
        backLabel = "← Back to Live Lists",
        onBack = onBack,
        onOpen = onOpen,
    )
}

@Composable
private fun DynamicListDetail(
    spec: DynamicListSpec,
    state: DynamicListState,
    mediaType: LibraryMediaType,
    onBack: () -> Unit,
    onOpen: (AstraWaveMetadataGateway.Item) -> Unit,
) {
    when (state) {
        DynamicListState.Loading -> Column(
            Modifier.fillMaxSize().background(AstraWaveColors.Background).padding(24.dp),
        ) {
            AstraWavePageHeader(title = spec.title, subtitle = spec.subtitle)
            Spacer(Modifier.height(18.dp))
            AstraWaveStatePanel("Refreshing ${spec.title}…", "Loading the newest list from live metadata.", loading = true)
        }
        is DynamicListState.Error -> Column(
            Modifier.fillMaxSize().background(AstraWaveColors.Background).padding(24.dp),
        ) {
            AstraWavePageHeader(title = spec.title, subtitle = spec.subtitle)
            Spacer(Modifier.height(10.dp))
            AstraWaveSecondaryButton(label = "← Back to Live Lists", onClick = onBack)
            Spacer(Modifier.height(18.dp))
            AstraWaveStatePanel("List temporarily unavailable", state.message)
        }
        is DynamicListState.Ready -> CollectionDetailBody(
            title = spec.title,
            subtitle = spec.subtitle,
            items = state.items,
            mediaType = mediaType,
            status = "Automatically refreshed",
            backLabel = "← Back to Live Lists",
            onBack = onBack,
            onOpen = onOpen,
        )
    }
}

@Composable
private fun CollectionDetailBody(
    title: String,
    subtitle: String,
    items: List<AstraWaveMetadataGateway.Item>,
    mediaType: LibraryMediaType,
    status: String,
    backLabel: String,
    onBack: () -> Unit,
    onOpen: (AstraWaveMetadataGateway.Item) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        AstraWavePageHeader(title = title, subtitle = subtitle)
        Spacer(Modifier.height(10.dp))
        AstraWaveSecondaryButton(label = backLabel, onClick = onBack)
        Spacer(Modifier.height(18.dp))
        Text(
            "${items.size} ${if (mediaType == LibraryMediaType.MOVIE) "movies" else "series"} • $status",
            color = AstraWaveColors.Success,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(12.dp))
        items.forEachIndexed { index, item ->
            AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onOpen(item) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", color = AstraWaveColors.Accent, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(40.dp))
                    Box(Modifier.width(118.dp)) { AstraWaveArtwork(title = item.name, modifier = Modifier.fillMaxWidth()) }
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                        item.releaseInfo?.let {
                            Spacer(Modifier.height(3.dp))
                            Text(it, color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelMedium)
                        }
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
        Spacer(Modifier.height(28.dp))
    }
}
