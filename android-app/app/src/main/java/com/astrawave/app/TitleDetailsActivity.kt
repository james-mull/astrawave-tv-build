package com.astrawave.app

import android.content.Intent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astrawave.app.core.ScrapeRequest
import com.astrawave.app.data.ResolvedSource
import com.astrawave.app.data.StremioEpisode
import com.astrawave.app.data.StremioSeriesEpisodeRepository
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

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = DetailsAccent, background = DetailsBg, surface = DetailsPanel)) {
                Surface(color = DetailsBg) {
                    TitleDetailsScreen(
                        title = title,
                        year = year,
                        mediaType = mediaType,
                        stremioType = stremioIdentity?.first,
                        stremioId = stremioIdentity?.second,
                        onBack = { finish() },
                        onPlay = { url ->
                            startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_URL, url))
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
    }
}

@Composable
private fun TitleDetailsScreen(
    title: String,
    year: Int?,
    mediaType: String?,
    stremioType: String?,
    stremioId: String?,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { UnifiedVodSourceRepository(context) }
    val episodeRepository = remember { StremioSeriesEpisodeRepository() }
    val seriesMode = mediaType == "SERIES" && !stremioId.isNullOrBlank()

    var refreshToken by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var episodeLoading by remember { mutableStateOf(seriesMode) }
    var error by remember { mutableStateOf<String?>(null) }
    var sources by remember { mutableStateOf<List<ResolvedSource>>(emptyList()) }
    var episodes by remember { mutableStateOf<List<StremioEpisode>>(emptyList()) }
    var selectedEpisode by remember { mutableStateOf<StremioEpisode?>(null) }
    var selectedSeason by remember { mutableIntStateOf(0) }

    LaunchedEffect(seriesMode, stremioId, refreshToken) {
        if (!seriesMode || stremioId.isNullOrBlank()) return@LaunchedEffect
        episodeLoading = true
        val loaded = withContext(Dispatchers.IO) {
            runCatching { episodeRepository.load(stremioId) }.getOrDefault(emptyList())
        }
        episodes = loaded
        if (selectedSeason == 0) selectedSeason = loaded.firstOrNull()?.season ?: 0
        episodeLoading = false
    }

    LaunchedEffect(title, year, stremioId, selectedEpisode, refreshToken) {
        if (seriesMode && selectedEpisode == null) {
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
            year = year,
            season = selectedEpisode?.season,
            episode = selectedEpisode?.episode,
            externalIds = if (!exactId.isNullOrBlank() && !exactType.isNullOrBlank()) {
                mapOf("stremio_id" to exactId, "stremio_type" to exactType)
            } else {
                emptyMap()
            },
        )
        sources = runCatching {
            withContext(Dispatchers.IO) { repository.discover(request) }
        }.onFailure {
            error = it.message ?: "Source discovery failed"
        }.getOrDefault(emptyList())
        loading = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(title, color = DetailsPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    selectedEpisode?.let { "Season ${it.season} • Episode ${it.episode} • ${it.title}" }
                        ?: year?.toString()
                        ?: if (seriesMode) "Choose an episode" else "Source discovery",
                    color = DetailsMuted,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { refreshToken++ }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
        }

        Text(
            if (seriesMode) {
                "Choose a season and episode. AstraWave resolves the exact Stremio episode id, tests eligible streams, removes duplicates, and ranks the best playable source first."
            } else {
                "AstraWave checks approved public sources and enabled Stremio catalogs, resolves eligible stream providers, removes duplicates, tests stream health, and ranks the best playable source first."
            },
            color = DetailsMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        if (seriesMode && selectedEpisode == null) {
            when {
                episodeLoading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text("Loading seasons and episodes…", color = DetailsPrimary)
                }
                episodes.isEmpty() -> EmptyPanel("No episode metadata found", "This series did not return a Cinemeta/Stremio episode list yet.")
                else -> SeriesEpisodeBrowser(
                    episodes = episodes,
                    selectedSeason = selectedSeason,
                    onSeason = { selectedSeason = it },
                    onEpisode = { selectedEpisode = it },
                )
            }
        } else {
            if (selectedEpisode != null) {
                TextButton(onClick = { selectedEpisode = null; sources = emptyList() }) {
                    Text("← Back to episodes")
                }
            }
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text("Resolving playable sources…", color = DetailsPrimary)
                }
                error != null -> Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                sources.isEmpty() -> EmptyPanel(
                    "No verified source found",
                    "AstraWave checked its approved resolver and enabled addon sources but did not find a healthy authorized direct stream for this ${if (selectedEpisode != null) "episode" else "title"}.",
                )
                else -> {
                    Text("Available Sources", color = DetailsPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    sources.forEachIndexed { index, source -> SourceCard(index = index, source = source, onPlay = onPlay) }
                }
            }
        }
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
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        seasons.forEach { season ->
            if (season == selectedSeason) {
                Button(onClick = { onSeason(season) }) { Text("Season $season") }
            } else {
                OutlinedButton(onClick = { onSeason(season) }) { Text("Season $season") }
            }
        }
    }
    episodes.filter { it.season == selectedSeason }.forEach { episode ->
        Column(
            Modifier.fillMaxWidth()
                .background(DetailsPanel, RoundedCornerShape(16.dp))
                .clickable { onEpisode(episode) }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("S${episode.season} E${episode.episode} • ${episode.title}", color = DetailsPrimary, fontWeight = FontWeight.Bold)
            episode.overview?.let {
                Text(it, color = DetailsMuted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
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
private fun SourceCard(index: Int, source: ResolvedSource, onPlay: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(DetailsPanel, RoundedCornerShape(18.dp))
            .clickable { onPlay(source.link.url) }
            .padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(42.dp).background(if (index == 0) DetailsAccent else Color(0xFF202736), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(source.link.sourceName, color = DetailsPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (index == 0) {
                    Text(
                        "BEST",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.background(DetailsAccent, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            val details = listOfNotNull(
                source.link.quality,
                source.contentType,
                source.latencyMs?.let { "${it}ms" },
                source.link.licenseLabel,
                "score ${source.score}",
            ).joinToString(" • ")
            Text(details, color = DetailsMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Health verified", color = DetailsSuccess, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
