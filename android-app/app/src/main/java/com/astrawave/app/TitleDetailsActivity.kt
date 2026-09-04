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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astrawave.app.core.ScrapeRequest
import com.astrawave.app.data.AppSettingsStore
import com.astrawave.app.data.ResolvedSource
import com.astrawave.app.data.StremioEpisode
import com.astrawave.app.data.StremioSeriesEpisodeRepository
import com.astrawave.app.data.TmdbCatalogRepository
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
                        onBack = { finish() },
                        onPlay = { urls, episode ->
                            if (urls.isEmpty()) return@TitleDetailsScreen
                            val playbackTitle = episode?.let {
                                "$title — S${it.season}E${it.episode} ${it.title}"
                            } ?: title
                            val baseId = sourceId?.takeIf { it.isNotBlank() }
                                ?: "title:${title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}"
                            val playbackId = episode?.let { "$baseId:s${it.season}e${it.episode}" } ?: baseId
                            val playbackType = if (episode != null) "EPISODE" else mediaType?.takeIf { it.isNotBlank() } ?: "MOVIE"
                            val playbackSourceId = episode?.id?.takeIf { it.isNotBlank() } ?: sourceId
                            startActivity(
                                Intent(this, PlayerActivity::class.java)
                                    .putExtra(PlayerActivity.EXTRA_URL, urls.first())
                                    .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(urls))
                                    .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true)
                                    .putExtra(PlayerActivity.EXTRA_PROFILE_ID, "default")
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
    onBack: () -> Unit,
    onPlay: (List<String>, StremioEpisode?) -> Unit,
) {
    val context = LocalContext.current
    val sourceRepository = remember { UnifiedVodSourceRepository(context) }
    val episodeRepository = remember { StremioSeriesEpisodeRepository() }
    val tmdbToken = remember { AppSettingsStore(context).effectiveTmdbBearerToken() }
    val tmdbRepository = remember(tmdbToken) { TmdbCatalogRepository(tmdbToken) }
    val seriesMode = mediaType == "SERIES" || stremioType.equals("series", true) || stremioType.equals("tv", true)
    val tmdbMediaType = if (seriesMode) "tv" else "movie"

    var sourcesMode by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var episodeLoading by remember { mutableStateOf(false) }
    var detailsLoading by remember { mutableStateOf(tmdbId != null && tmdbRepository.isConfigured()) }
    var error by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<TmdbTitleDetails?>(null) }
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

    LaunchedEffect(sourcesMode, seriesMode, stremioId, refreshToken) {
        if (!sourcesMode || !seriesMode || stremioId.isNullOrBlank()) return@LaunchedEffect
        episodeLoading = true
        val loaded = withContext(Dispatchers.IO) {
            runCatching { episodeRepository.load(stremioId) }.getOrDefault(emptyList())
        }
        episodes = loaded
        if (selectedSeason == 0) selectedSeason = loaded.firstOrNull()?.season ?: 0
        episodeLoading = false
    }

    LaunchedEffect(sourcesMode, title, year, stremioId, selectedEpisode, refreshToken) {
        if (!sourcesMode || (seriesMode && !stremioId.isNullOrBlank() && selectedEpisode == null)) {
            loading = false
            sources = emptyList()
            return@LaunchedEffect
        }
        loading = true
        error = null
        val exactId = selectedEpisode?.id ?: stremioId
        val exactType = if (selectedEpisode != null) "series" else stremioType
        val request = ScrapeRequest(
            title = selectedEpisode?.title ?: title,
            year = year ?: details?.releaseDate?.take(4)?.toIntOrNull(),
            season = selectedEpisode?.season,
            episode = selectedEpisode?.episode,
            externalIds = if (!exactId.isNullOrBlank() && !exactType.isNullOrBlank()) {
                mapOf("stremio_id" to exactId, "stremio_type" to exactType)
            } else emptyMap(),
        )
        sources = runCatching { withContext(Dispatchers.IO) { sourceRepository.discover(request) } }
            .onFailure { error = it.message ?: "Source discovery failed" }
            .getOrDefault(emptyList())
        loading = false
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
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
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
            if (sourcesMode) {
                IconButton(onClick = { refreshToken++ }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh sources") }
            }
        }

        if (!sourcesMode) {
            when {
                detailsLoading -> LoadingRow("Loading premium title details…")
                details != null -> PremiumInfoPanel(details = details!!, seriesMode = seriesMode)
                else -> InfoPanel(title = title, year = year, seriesMode = seriesMode)
            }

            details?.videos
                ?.firstOrNull { it.site.equals("YouTube", true) && (it.type.equals("Trailer", true) || it.official) }
                ?.let { trailer ->
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${trailer.key}")))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("▶ Watch Trailer")
                    }
                }

            Button(
                onClick = { sourcesMode = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(if (seriesMode && !stremioId.isNullOrBlank()) "Episodes & Sources" else "Find Watch Options", fontWeight = FontWeight.Bold)
            }
            Text(
                "Playback discovery starts only when you ask for watch options. AstraWave ranks eligible healthy sources and automatically keeps backups ready.",
                color = DetailsMuted,
                fontSize = 12.sp,
            )
            return@Column
        }

        Text(
            if (seriesMode && !stremioId.isNullOrBlank()) {
                "Choose an episode, then AstraWave resolves that exact episode and ranks healthy eligible sources."
            } else {
                "AstraWave checks approved sources and enabled addons, removes duplicates, verifies stream health, and ranks the best playable source first."
            },
            color = DetailsMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        if (seriesMode && !stremioId.isNullOrBlank() && selectedEpisode == null) {
            when {
                episodeLoading -> LoadingRow("Loading seasons and episodes…")
                episodes.isEmpty() -> EmptyPanel("No episode metadata found", "This series did not return a compatible episode list yet.")
                else -> SeriesEpisodeBrowser(
                    episodes = episodes,
                    selectedSeason = selectedSeason,
                    onSeason = { selectedSeason = it },
                    onEpisode = { selectedEpisode = it },
                )
            }
        } else {
            if (selectedEpisode != null) {
                TextButton(onClick = { selectedEpisode = null; sources = emptyList(); error = null }) { Text("← Back to episodes") }
            }
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
        if (metadata.isNotEmpty()) {
            Text(metadata.joinToString("  •  "), color = DetailsMuted, fontSize = 13.sp)
        }
        Text(
            details.overview.ifBlank { "No synopsis is available yet." },
            color = DetailsPrimary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
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
            Spacer(Modifier.height(3.dp))
            Text("Top Cast", color = DetailsPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                details.cast.take(8).joinToString("  •  ") { it.name },
                color = DetailsMuted,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
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
    onSeason: (Int) -> Unit,
    onEpisode: (StremioEpisode) -> Unit,
) {
    val seasons = episodes.map { it.season }.filter { it > 0 }.distinct().sorted()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        seasons.forEach { season ->
            if (season == selectedSeason) Button(onClick = { onSeason(season) }) { Text("Season $season") }
            else OutlinedButton(onClick = { onSeason(season) }) { Text("Season $season") }
        }
    }
    episodes.filter { it.season == selectedSeason }.forEach { episode ->
        Column(
            Modifier.fillMaxWidth().background(DetailsPanel, RoundedCornerShape(16.dp)).clickable { onEpisode(episode) }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("S${episode.season} E${episode.episode} • ${episode.title}", color = DetailsPrimary, fontWeight = FontWeight.Bold)
            episode.overview?.let { Text(it, color = DetailsMuted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Text("Resolve episode sources", color = DetailsAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
