package com.astrawave.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.astrawave.app.core.ScrapeRequest
import com.astrawave.app.data.AppSettingsStore
import com.astrawave.app.data.LocalLibraryStore
import com.astrawave.app.data.MovieDetailContextRepository
import com.astrawave.app.data.ResolvedSource
import com.astrawave.app.data.StremioEpisode
import com.astrawave.app.data.StremioSeriesEpisodeRepository
import com.astrawave.app.data.TmdbCatalogRepository
import com.astrawave.app.data.TmdbSeriesEpisodeRepository
import com.astrawave.app.data.TmdbTitleDetails
import com.astrawave.app.data.UnifiedVodSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DetailsBg = Color(0xFF080A0F)
private val DetailsPanel = Color(0xFF141924)
private val DetailsPrimary = Color(0xFFF7F8FB)
private val DetailsMuted = Color(0xFFA7AEBB)
private val DetailsAccent = Color(0xFF8B5CF6)
private val DetailsSuccess = Color(0xFF39D98A)

class TitleDetailsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "AstraWave Title" }
        val year = intent.getIntExtra(EXTRA_YEAR, 0).takeIf { it > 0 }
        val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE)
        val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID)
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty().ifBlank { "default" }
        val stremioIdentity = parseStremioIdentity(sourceId)
        val tmdbId = parseTmdbId(sourceId)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = DetailsAccent, background = DetailsBg, surface = DetailsPanel)) {
                Surface(color = DetailsBg) {
                    TitleDetailsScreen(
                        title = title,
                        year = year,
                        mediaType = mediaType,
                        stremioType = stremioIdentity?.first,
                        stremioId = stremioIdentity?.second,
                        tmdbId = tmdbId,
                        profileId = profileId,
                        onBack = { finish() },
                        onPlay = { urls, episode ->
                            if (urls.isEmpty()) return@TitleDetailsScreen
                            val playbackTitle = episode?.let { "$title — S${it.season}E${it.episode} ${it.title}" } ?: title
                            val baseId = sourceId?.takeIf { it.isNotBlank() }
                                ?: "title:${title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}"
                            val playbackId = episode?.id?.takeIf { it.isNotBlank() } ?: baseId
                            val playbackType = if (episode != null) "EPISODE" else mediaType?.takeIf { it.isNotBlank() } ?: "MOVIE"
                            val playbackSourceId = episode?.id?.takeIf { it.isNotBlank() } ?: sourceId
                            startActivity(
                                Intent(this, PlayerActivity::class.java)
                                    .putExtra(PlayerActivity.EXTRA_URL, urls.first())
                                    .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(urls))
                                    .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true)
                                    .putExtra(PlayerActivity.EXTRA_PROFILE_ID, profileId)
                                    .putExtra(PlayerActivity.EXTRA_LIBRARY_ID, playbackId)
                                    .putExtra(PlayerActivity.EXTRA_LIBRARY_TITLE, playbackTitle)
                                    .putExtra(PlayerActivity.EXTRA_LIBRARY_TYPE, playbackType)
                                    .putExtra(PlayerActivity.EXTRA_LIBRARY_SOURCE_ID, playbackSourceId),
                            )
                        },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_YEAR = "year"
        const val EXTRA_MEDIA_TYPE = "media_type"
        const val EXTRA_SOURCE_ID = "source_id"
        const val EXTRA_PROFILE_ID = "profile_id"

        private fun parseStremioIdentity(sourceId: String?): Pair<String, String>? {
            if (sourceId?.startsWith("stremio:") != true) return null
            val parts = sourceId.split(":")
            if (parts.size < 4) return null
            val type = parts[2]
            val id = parts.drop(3).joinToString(":")
            return if (type.isBlank() || id.isBlank()) null else type to id
        }

        private fun parseTmdbId(sourceId: String?): Long? {
            if (sourceId?.startsWith("tmdb:") != true) return null
            return sourceId.substringAfter("tmdb:").substringAfterLast(':').toLongOrNull()
        }
    }
}

@Composable
private fun TitleDetailsScreen(
    title: String,
    year: Int?,
    mediaType: String?,
    stremioType: String?,
    stremioId: String?,
    tmdbId: Long?,
    profileId: String,
    onBack: () -> Unit,
    onPlay: (List<String>, StremioEpisode?) -> Unit,
) {
    val context = LocalContext.current
    val sourceRepository = remember { UnifiedVodSourceRepository(context) }
    val stremioEpisodeRepository = remember { StremioSeriesEpisodeRepository() }
    val nativeEpisodeRepository = remember { TmdbSeriesEpisodeRepository() }
    val movieContextRepository = remember { MovieDetailContextRepository() }
    val localLibrary = remember { LocalLibraryStore(context) }
    val tmdbToken = remember { AppSettingsStore(context).effectiveTmdbBearerToken() }
    val tmdbRepository = remember(tmdbToken) { TmdbCatalogRepository(tmdbToken) }
    val seriesMode = mediaType.equals("series", true) || mediaType.equals("tv", true) ||
        stremioType.equals("series", true) || stremioType.equals("tv", true)
    val tmdbMediaType = if (seriesMode) "tv" else "movie"
    val hasEpisodeCatalog = seriesMode && (!stremioId.isNullOrBlank() || tmdbId != null)

    var sourcesMode by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var episodeLoading by remember { mutableStateOf(false) }
    var detailsLoading by remember { mutableStateOf(tmdbId != null && tmdbRepository.isConfigured()) }
    var movieContextLoading by remember { mutableStateOf(!seriesMode && tmdbId != null) }
    var error by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<TmdbTitleDetails?>(null) }
    var movieContext by remember { mutableStateOf(MovieDetailContextRepository.Context()) }
    var sources by remember { mutableStateOf<List<ResolvedSource>>(emptyList()) }
    var episodes by remember { mutableStateOf<List<StremioEpisode>>(emptyList()) }
    var selectedEpisode by remember { mutableStateOf<StremioEpisode?>(null) }
    var selectedSeason by remember { mutableIntStateOf(0) }

    LaunchedEffect(tmdbId, tmdbToken) {
        if (tmdbId == null || !tmdbRepository.isConfigured()) {
            detailsLoading = false
            return@LaunchedEffect
        }
        detailsLoading = true
        details = withContext(Dispatchers.IO) {
            runCatching { tmdbRepository.loadDetails(tmdbMediaType, tmdbId) }.getOrNull()
        }
        detailsLoading = false
    }

    LaunchedEffect(tmdbId, seriesMode) {
        if (seriesMode || tmdbId == null) {
            movieContextLoading = false
            return@LaunchedEffect
        }
        movieContextLoading = true
        movieContext = withContext(Dispatchers.IO) {
            runCatching { movieContextRepository.load(tmdbId) }.getOrDefault(MovieDetailContextRepository.Context())
        }
        movieContextLoading = false
    }

    LaunchedEffect(hasEpisodeCatalog, stremioId, tmdbId, refreshToken) {
        if (!hasEpisodeCatalog) return@LaunchedEffect
        episodeLoading = true
        val loaded = withContext(Dispatchers.IO) {
            when {
                !stremioId.isNullOrBlank() -> runCatching { stremioEpisodeRepository.load(stremioId) }.getOrDefault(emptyList())
                tmdbId != null -> runCatching { nativeEpisodeRepository.load(tmdbId) }.getOrDefault(emptyList())
                else -> emptyList()
            }
        }
        episodes = loaded
        if (selectedSeason == 0 || loaded.none { it.season == selectedSeason }) selectedSeason = loaded.firstOrNull()?.season ?: 0
        episodeLoading = false
    }

    LaunchedEffect(sourcesMode, title, year, stremioId, selectedEpisode, refreshToken) {
        if (!sourcesMode || (hasEpisodeCatalog && selectedEpisode == null)) {
            loading = false
            sources = emptyList()
            return@LaunchedEffect
        }
        loading = true
        error = null
        val episodeIsStremio = selectedEpisode?.id?.startsWith("tmdbtv:", ignoreCase = true) == false
        val exactId = selectedEpisode?.id?.takeIf { episodeIsStremio } ?: stremioId?.takeIf { selectedEpisode == null }
        val exactType = when {
            selectedEpisode != null && episodeIsStremio -> "series"
            selectedEpisode == null -> stremioType
            else -> null
        }
        val request = ScrapeRequest(
            title = title,
            year = year ?: details?.releaseDate?.take(4)?.toIntOrNull(),
            season = selectedEpisode?.season,
            episode = selectedEpisode?.episode,
            externalIds = if (!exactId.isNullOrBlank() && !exactType.isNullOrBlank()) {
                mapOf("stremio_id" to exactId, "stremio_type" to exactType)
            } else emptyMap(),
        )
        sources = runCatching { withContext(Dispatchers.IO) { sourceRepository.discover(request, profileId) } }
            .onFailure { error = it.message ?: "Source discovery failed" }
            .getOrDefault(emptyList())
        loading = false
    }

    val progressByEpisode = remember(episodes, sourcesMode, profileId) {
        localLibrary.progress(profileId).associateBy { it.item.id }
    }
    val firstUnwatched = remember(episodes, progressByEpisode) {
        val ordered = episodes.filter { it.season > 0 && it.episode > 0 }
            .sortedWith(compareBy<StremioEpisode> { it.season }.thenBy { it.episode })
        ordered.firstOrNull { progressByEpisode[it.id]?.let { saved -> saved.positionMs > 0 && !saved.completed } == true }
            ?: ordered.firstOrNull { progressByEpisode[it.id]?.completed != true }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (sourcesMode) {
                    sourcesMode = false
                    selectedEpisode = null
                    sources = emptyList()
                    error = null
                } else onBack()
            }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(title, color = DetailsPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        selectedEpisode != null -> "Season ${selectedEpisode!!.season} • Episode ${selectedEpisode!!.episode} • ${selectedEpisode!!.title}"
                        sourcesMode && seriesMode -> "Episodes & Sources"
                        sourcesMode -> "Sources"
                        details?.releaseDate?.isNotBlank() == true -> details!!.releaseDate.orEmpty()
                        year != null -> year.toString()
                        else -> if (seriesMode) "Series details" else "Movie details"
                    },
                    color = DetailsMuted,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (sourcesMode) IconButton(onClick = { refreshToken++ }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh sources") }
        }

        if (!sourcesMode) {
            when {
                detailsLoading -> LoadingRow("Loading premium title details…")
                details != null -> PremiumInfoPanel(details = details!!, seriesMode = seriesMode)
                else -> InfoPanel(title = title, year = year, seriesMode = seriesMode)
            }

            if (!seriesMode) {
                when {
                    movieContextLoading -> LoadingRow("Finding AstraWave lists, related movies and franchises…")
                    movieContext.lists.isNotEmpty() -> MovieListMembershipPanel(movieContext.lists, profileId)
                }
                movieContext.collection?.let { collection ->
                    MovieCollectionPanel(collection, tmdbId?.toString(), profileId)
                }
                movieContext.universe?.takeIf { it.parts.isNotEmpty() }?.let { universe ->
                    MovieUniversePanel(universe, tmdbId?.toString(), profileId)
                }
                movieContext.related.similar.takeIf { it.isNotEmpty() }?.let {
                    RelatedMoviePanel("Similar Movies", "Movies with related themes, audiences and metadata", it, profileId)
                }
                movieContext.related.director?.takeIf { it.movies.isNotEmpty() }?.let {
                    RelatedMoviePanel("More From ${it.name}", "Other movies directed by ${it.name}", it.movies, profileId)
                }
                movieContext.related.actor?.takeIf { it.movies.isNotEmpty() }?.let {
                    RelatedMoviePanel("More With ${it.name}", "Other movies featuring ${it.name}", it.movies, profileId)
                }
            }

            details?.videos
                ?.firstOrNull { it.site.equals("YouTube", true) && (it.type.equals("Trailer", true) || it.official) }
                ?.let { trailer ->
                    OutlinedButton(
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${trailer.key}"))) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("▶ Watch Trailer") }
                }

            if (hasEpisodeCatalog && firstUnwatched != null) {
                Button(
                    onClick = {
                        selectedSeason = firstUnwatched.season
                        selectedEpisode = firstUnwatched
                        sourcesMode = true
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    val saved = progressByEpisode[firstUnwatched.id]
                    val prefix = if (saved != null && saved.positionMs > 0 && !saved.completed) "Continue" else "Start"
                    Text("$prefix Series • S${firstUnwatched.season}E${firstUnwatched.episode} ${firstUnwatched.title}", fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { sourcesMode = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(if (hasEpisodeCatalog) "Browse All Episodes & Sources" else "Find Watch Options", fontWeight = FontWeight.Bold) }
            Text(
                if (hasEpisodeCatalog) {
                    "AstraWave picks the first unfinished episode, tracks each season separately, and uses watch state to choose a smarter Up Next episode."
                } else {
                    "Playback discovery starts only when you ask for watch options. AstraWave ranks eligible healthy sources and automatically keeps backups ready."
                },
                color = DetailsMuted,
                fontSize = 12.sp,
            )
            return@Column
        }

        Text(
            if (hasEpisodeCatalog) {
                "Choose an episode, then AstraWave resolves that episode and ranks healthy eligible sources. Completed episodes are skipped when a later unwatched episode is available."
            } else {
                "AstraWave checks approved sources and enabled addons, removes duplicates, verifies stream health, and ranks the best playable source first."
            },
            color = DetailsMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        if (hasEpisodeCatalog && selectedEpisode == null) {
            when {
                episodeLoading -> LoadingRow("Loading seasons and episodes…")
                episodes.isEmpty() -> EmptyPanel("No episode metadata found", "This series did not return a compatible native or Cinemeta episode list yet.")
                else -> SeriesEpisodeBrowser(
                    episodes = episodes,
                    selectedSeason = selectedSeason,
                    progress = progressByEpisode,
                    onSeason = { selectedSeason = it },
                    onEpisode = { selectedEpisode = it },
                )
            }
        } else {
            if (selectedEpisode != null) TextButton(onClick = { selectedEpisode = null; sources = emptyList(); error = null }) { Text("← Back to episodes") }
            when {
                loading -> LoadingRow("Resolving playable sources…")
                error != null -> Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                sources.isEmpty() -> EmptyPanel(
                    "No verified source found",
                    "AstraWave checked its approved resolver and enabled addon sources but did not find a healthy eligible direct stream for this ${if (selectedEpisode != null) "episode" else "title"}.",
                )
                else -> {
                    val allUrls = sources.map { it.link.url }.distinct()
                    Text("Available Sources", color = DetailsPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${allUrls.size} verified option${if (allUrls.size == 1) "" else "s"} • automatic backup failover enabled",
                        color = DetailsSuccess,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    sources.forEachIndexed { index, source ->
                        val orderedUrls = listOf(source.link.url) + allUrls.filterNot { it == source.link.url }
                        SourceCard(index, source) { onPlay(orderedUrls.distinct(), selectedEpisode) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieListMembershipPanel(lists: List<MovieDetailContextRepository.ListMembership>, profileId: String) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().background(DetailsPanel, RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("In These AstraWave Lists", color = DetailsPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text("Tap any list to jump directly into that collection.", color = DetailsMuted, fontSize = 12.sp)
        lists.forEach { item ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    context.startActivity(
                        Intent(context, MovieListDetailActivity::class.java)
                            .putExtra(MovieListDetailActivity.EXTRA_TITLE, item.title)
                            .putExtra(MovieListDetailActivity.EXTRA_REASON, item.reason)
                            .putExtra(MovieListDetailActivity.EXTRA_QUERY, item.query)
                            .putExtra(MovieListDetailActivity.EXTRA_GENRE, item.genre)
                            .putExtra(MovieListDetailActivity.EXTRA_PROFILE_ID, profileId),
                    )
                }.padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(item.title, color = DetailsAccent, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.42f))
                Column(Modifier.weight(0.58f)) {
                    Text("${item.category} • Open list →", color = DetailsPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (item.reason.isNotBlank()) Text(item.reason, color = DetailsMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun MovieCollectionPanel(
    collection: MovieDetailContextRepository.MovieCollection,
    currentMovieId: String?,
    profileId: String,
) {
    Column(
        Modifier.fillMaxWidth().background(DetailsPanel, RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Movie Series / Collection", color = DetailsPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(collection.name, color = DetailsAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("${collection.parts.size} movie${if (collection.parts.size == 1) "" else "s"} • official collection • release order", color = DetailsMuted, fontSize = 12.sp)
        MovieCards(collection.parts, currentMovieId, profileId)
    }
}

@Composable
private fun MovieUniversePanel(
    universe: MovieDetailContextRepository.MovieUniverse,
    currentMovieId: String?,
    profileId: String,
) {
    Column(
        Modifier.fillMaxWidth().background(DetailsPanel, RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Connected Universe", color = DetailsPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(universe.name, color = DetailsAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            if (universe.editorial) "AstraWave editorial universe grouping • broader than one official TMDB collection" else "Connected collection",
            color = DetailsMuted,
            fontSize = 12.sp,
        )
        MovieCards(universe.parts, currentMovieId, profileId)
    }
}

@Composable
private fun RelatedMoviePanel(
    title: String,
    subtitle: String,
    movies: List<MovieDetailContextRepository.CollectionMovie>,
    profileId: String,
) {
    Column(
        Modifier.fillMaxWidth().background(DetailsPanel, RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = DetailsPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = DetailsMuted, fontSize = 12.sp)
        MovieCards(movies, null, profileId)
    }
}

@Composable
private fun MovieCards(
    movies: List<MovieDetailContextRepository.CollectionMovie>,
    currentMovieId: String?,
    profileId: String,
) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        movies.forEachIndexed { index, movie ->
            Column(
                Modifier.width(150.dp).clickable {
                    context.startActivity(
                        Intent(context, TitleDetailsActivity::class.java)
                            .putExtra(TitleDetailsActivity.EXTRA_TITLE, movie.title)
                            .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, "MOVIE")
                            .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, "tmdb:${movie.id}")
                            .putExtra(TitleDetailsActivity.EXTRA_PROFILE_ID, profileId),
                    )
                },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AsyncImage(
                    model = movie.posterUrl ?: movie.backdropUrl,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(210.dp).background(Color(0xFF202736), RoundedCornerShape(12.dp)),
                )
                Text("${index + 1}. ${movie.title}", color = DetailsPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val year = movie.releaseDate?.take(4)
                Text(
                    listOfNotNull(year, if (movie.id == currentMovieId) "CURRENT" else null).joinToString(" • ").ifBlank { "Open details" },
                    color = if (movie.id == currentMovieId) DetailsSuccess else DetailsMuted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun PremiumInfoPanel(details: TmdbTitleDetails, seriesMode: Boolean) {
    Column(
        Modifier.fillMaxWidth().background(DetailsPanel, RoundedCornerShape(20.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (seriesMode) "SERIES" else "MOVIE", color = DetailsAccent, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text(details.title, color = DetailsPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        val metadata = buildList {
            details.releaseDate?.takeIf { it.isNotBlank() }?.let { add(it.take(4)) }
            details.runtimeMinutes?.let { add("${it} min") }
            if (details.genres.isNotEmpty()) add(details.genres.take(3).joinToString(" • "))
        }
        if (metadata.isNotEmpty()) Text(metadata.joinToString("  •  "), color = DetailsMuted, fontSize = 13.sp)
        Text(details.overview.ifBlank { "No synopsis is available yet." }, color = DetailsPrimary, fontSize = 14.sp, lineHeight = 21.sp)
        val creators = details.crew.take(4)
        if (creators.isNotEmpty()) {
            Text(
                creators.joinToString("  •  ") { person -> listOfNotNull(person.name, person.role).joinToString(" — ") },
                color = DetailsMuted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (details.cast.isNotEmpty()) {
            Text("Top Cast", color = DetailsPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(details.cast.take(8).joinToString("  •  ") { it.name }, color = DetailsMuted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun InfoPanel(title: String, year: Int?, seriesMode: Boolean) {
    Column(
        Modifier.fillMaxWidth().background(DetailsPanel, RoundedCornerShape(20.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (seriesMode) "SERIES" else "MOVIE", color = DetailsAccent, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text(title, color = DetailsPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        year?.let { Text(it.toString(), color = DetailsMuted, fontSize = 14.sp) }
        Text(
            if (seriesMode) "Browse the title first, then open Episodes & Sources when you are ready to choose an episode and playback source."
            else "Review the title first, then open watch options to let AstraWave find and rank available playback choices.",
            color = DetailsMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun LoadingRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(label, color = DetailsPrimary)
    }
}

@Composable
private fun SeriesEpisodeBrowser(
    episodes: List<StremioEpisode>,
    selectedSeason: Int,
    progress: Map<String, LocalLibraryStore.PlaybackProgress>,
    onSeason: (Int) -> Unit,
    onEpisode: (StremioEpisode) -> Unit,
) {
    val seasons = episodes.map { it.season }.filter { it > 0 }.distinct().sorted()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        seasons.forEach { season ->
            val seasonEpisodes = episodes.filter { it.season == season }
            val completed = seasonEpisodes.count { progress[it.id]?.completed == true }
            val label = "Season $season • $completed/${seasonEpisodes.size}"
            if (season == selectedSeason) Button(onClick = { onSeason(season) }) { Text(label) }
            else OutlinedButton(onClick = { onSeason(season) }) { Text(label) }
        }
    }
    episodes.filter { it.season == selectedSeason }.forEach { episode ->
        val saved = progress[episode.id]
        val percent = saved?.takeIf { it.durationMs > 0L }?.let { ((it.positionMs * 100L) / it.durationMs).coerceIn(0L, 100L) }
        Row(
            Modifier.fillMaxWidth().background(DetailsPanel, RoundedCornerShape(16.dp)).clickable { onEpisode(episode) }.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!episode.thumbnail.isNullOrBlank()) {
                AsyncImage(
                    model = episode.thumbnail,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(150.dp).height(84.dp).background(Color(0xFF202736), RoundedCornerShape(10.dp)),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("S${episode.season} E${episode.episode} • ${episode.title}", color = DetailsPrimary, fontWeight = FontWeight.Bold)
                episode.released?.takeIf { it.isNotBlank() }?.let { Text(it.take(10), color = DetailsMuted, fontSize = 11.sp) }
                episode.overview?.let { Text(it, color = DetailsMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Text(
                    when {
                        saved?.completed == true -> "Watched • Play again"
                        percent != null && percent > 0 -> "Resume • $percent% watched"
                        else -> "Play episode"
                    },
                    color = if (percent != null && percent > 0) DetailsSuccess else DetailsAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EmptyPanel(title: String, message: String) {
    Column(
        Modifier.fillMaxWidth().background(DetailsPanel, RoundedCornerShape(18.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = DetailsPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(message, color = DetailsMuted, fontSize = 13.sp)
    }
}

@Composable
private fun SourceCard(index: Int, source: ResolvedSource, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(DetailsPanel, RoundedCornerShape(18.dp)).clickable { onPlay() }.padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(42.dp).background(if (index == 0) DetailsAccent else Color(0xFF202736), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(source.link.sourceName, color = DetailsPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (index == 0) Text("BEST", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.background(DetailsAccent, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
            }
            val details = listOfNotNull(source.link.quality, source.contentType, source.latencyMs?.let { "${it}ms" }, source.link.licenseLabel, "score ${source.score}").joinToString(" • ")
            Text(details, color = DetailsMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Health verified • backups ready", color = DetailsSuccess, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
