package com.astrawave.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.ScrapeRequest
import com.astrawave.app.data.AppSettingsStore
import com.astrawave.app.data.LocalLibraryStore
import com.astrawave.app.data.ResolvedSource
import com.astrawave.app.data.TmdbCatalogRepository
import com.astrawave.app.data.TmdbTitleDetails
import com.astrawave.app.data.UnifiedVodSourceRepository
import com.astrawave.app.data.VodPlaybackCoordinator
import com.astrawave.app.data.VodPlaybackRequest
import com.astrawave.app.ui.AstraWaveArtwork
import com.astrawave.app.ui.AstraWaveArtworkKind
import com.astrawave.app.ui.AstraWaveColors
import com.astrawave.app.ui.AstraWavePrimaryButton
import com.astrawave.app.ui.AstraWaveSecondaryButton
import com.astrawave.app.ui.AstraWaveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PremiumVodDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "AstraWave Title" }
        val year = intent.getIntExtra(EXTRA_YEAR, 0).takeIf { it > 0 }
        val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE).orEmpty().ifBlank { "MOVIE" }
        val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID)
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty().ifBlank { "default" }
        setContent {
            AstraWaveTheme {
                Surface(color = AstraWaveColors.Background) {
                    PremiumVodDetailScreen(title, year, mediaType, sourceId, profileId) { finish() }
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
    }
}

@Composable
private fun PremiumVodDetailScreen(
    title: String,
    year: Int?,
    mediaType: String,
    sourceId: String?,
    profileId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember { VodPlaybackCoordinator(context) }
    val sourceRepository = remember { UnifiedVodSourceRepository(context) }
    val library = remember { LocalLibraryStore(context) }
    val tmdbToken = remember { AppSettingsStore(context).effectiveTmdbBearerToken() }
    val tmdbRepository = remember(tmdbToken) { TmdbCatalogRepository(tmdbToken) }
    val tmdbId = remember(sourceId) { sourceId?.takeIf { it.startsWith("tmdb:") }?.substringAfterLast(':')?.toLongOrNull() }
    val seriesMode = mediaType.equals("SERIES", true) || mediaType.equals("TV", true)
    var details by remember { mutableStateOf<TmdbTitleDetails?>(null) }
    var loadingDetails by remember { mutableStateOf(tmdbId != null && tmdbRepository.isConfigured()) }
    var playLoading by remember { mutableStateOf(false) }
    var playError by remember { mutableStateOf<String?>(null) }
    var showSources by remember { mutableStateOf(false) }
    var sourcesLoading by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf<List<ResolvedSource>>(emptyList()) }
    var sourceError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tmdbId, tmdbToken) {
        if (tmdbId == null || !tmdbRepository.isConfigured()) {
            loadingDetails = false
            return@LaunchedEffect
        }
        loadingDetails = true
        details = withContext(Dispatchers.IO) {
            runCatching { tmdbRepository.loadDetails(if (seriesMode) "tv" else "movie", tmdbId) }.getOrNull()
        }
        loadingDetails = false
    }

    val progress = remember(title, profileId) {
        library.progress(profileId).firstOrNull { saved -> saved.item.title.startsWith(title, ignoreCase = true) && !saved.completed }
    }

    fun openDeepDetails() {
        context.startActivity(
            Intent(context, TitleDetailsActivity::class.java)
                .putExtra(TitleDetailsActivity.EXTRA_TITLE, title)
                .putExtra(TitleDetailsActivity.EXTRA_YEAR, year ?: 0)
                .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, mediaType)
                .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, sourceId)
                .putExtra(TitleDetailsActivity.EXTRA_PROFILE_ID, profileId),
        )
    }

    fun loadSources() {
        if (seriesMode) {
            openDeepDetails()
            return
        }
        showSources = true
        sourcesLoading = true
        sourceError = null
        scope.launch {
            val request = ScrapeRequest(
                title = title,
                year = year ?: details?.releaseDate?.take(4)?.toIntOrNull(),
            )
            val result = runCatching { withContext(Dispatchers.IO) { sourceRepository.discover(request, profileId) } }
            sources = result.getOrDefault(emptyList())
            sourceError = result.exceptionOrNull()?.message
            sourcesLoading = false
        }
    }

    fun playResolvedSource(source: ResolvedSource) {
        val ordered = listOf(source.link.url) + sources.map { it.link.url }.filterNot { it == source.link.url }
        context.startActivity(
            Intent(context, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URL, ordered.first())
                .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(ordered.distinct()))
                .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true)
                .putExtra(PlayerActivity.EXTRA_PROFILE_ID, profileId)
                .putExtra(PlayerActivity.EXTRA_LIBRARY_TITLE, title)
                .putExtra(PlayerActivity.EXTRA_LIBRARY_TYPE, if (seriesMode) "SERIES" else "MOVIE")
                .putExtra(PlayerActivity.EXTRA_LIBRARY_SOURCE_ID, sourceId),
        )
    }

    fun playBest() {
        if (playLoading) return
        playLoading = true
        playError = null
        scope.launch {
            val request = VodPlaybackRequest(
                title = title,
                year = year ?: details?.releaseDate?.take(4)?.toIntOrNull(),
                mediaType = if (seriesMode) "series" else "movie",
                profileId = profileId,
                sourceId = sourceId,
            )
            val intent = runCatching { withContext(Dispatchers.IO) { coordinator.prepareIntent(request).second } }.getOrNull()
            playLoading = false
            if (intent != null) context.startActivity(intent)
            else {
                playError = if (seriesMode) "Choose an episode to continue." else "No automatic source was ready. Review Sources for available options."
                if (seriesMode) openDeepDetails() else loadSources()
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(AstraWaveColors.Background)) {
        Box(Modifier.fillMaxWidth().height(520.dp)) {
            AstraWaveArtwork(title, Modifier.fillMaxSize(), AstraWaveArtworkKind.Backdrop)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            AstraWaveColors.Background.copy(alpha = 0.24f),
                            AstraWaveColors.Background.copy(alpha = 0.72f),
                            AstraWaveColors.Background,
                        ),
                    ),
                ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 34.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(if (seriesMode) "SERIES" else "MOVIE", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelLarge)
                Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    details?.releaseDate?.take(4) ?: year?.toString(),
                    details?.runtimeMinutes?.let { "${it} min" },
                    details?.genres?.take(3)?.joinToString(" • ")?.takeIf { it.isNotBlank() },
                ).joinToString("  •  ")
                if (meta.isNotBlank()) Text(meta, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AstraWavePrimaryButton(
                        label = when {
                            playLoading -> "Finding Best Source…"
                            seriesMode && progress != null -> "▶ Continue Series"
                            seriesMode -> "▶ Browse Episodes"
                            progress != null -> "▶ Resume"
                            else -> "▶ Play Best"
                        },
                        onClick = { if (seriesMode) openDeepDetails() else playBest() },
                        enabled = !playLoading,
                    )
                    details?.videos?.firstOrNull { it.site.equals("YouTube", true) && (it.type.equals("Trailer", true) || it.official) }?.let { trailer ->
                        AstraWaveSecondaryButton("Trailer") {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${trailer.key}"))) }
                        }
                    }
                    AstraWaveSecondaryButton(if (showSources) "Hide Sources" else "Sources") {
                        if (showSources) showSources = false else loadSources()
                    }
                    AstraWaveSecondaryButton("More") { openDeepDetails() }
                }
            }
        }

        if (showSources && !seriesMode) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("AVAILABLE SOURCES", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${sources.size} found", color = if (sources.isNotEmpty()) AstraWaveColors.Success else AstraWaveColors.TertiaryText)
                }
                when {
                    sourcesLoading -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(22.dp), strokeWidth = 2.dp, color = AstraWaveColors.AccentStrong)
                        Text("Checking your authorized providers…", color = AstraWaveColors.SecondaryText)
                    }
                    sources.isEmpty() -> {
                        Text(
                            sourceError ?: "No authorized direct sources were returned. Connect or enable a compatible Stremio/Xtream provider under My AstraWave → Content Sources, then refresh.",
                            color = AstraWaveColors.Warning,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        AstraWaveSecondaryButton("Refresh Sources") { loadSources() }
                    }
                    else -> sources.take(24).forEachIndexed { index, source ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(AstraWaveColors.Surface, MaterialTheme.shapes.medium)
                                .clickable { playResolvedSource(source) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (index == 0) "BEST • ${source.link.sourceName}" else source.link.sourceName,
                                    color = if (index == 0) AstraWaveColors.Success else AstraWaveColors.PrimaryText,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    listOfNotNull(source.link.quality, source.link.licenseLabel).joinToString(" • "),
                                    color = AstraWaveColors.SecondaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text("PLAY  ›", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                loadingDetails -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp, color = AstraWaveColors.AccentStrong)
                    Text("Loading details…", color = AstraWaveColors.SecondaryText)
                }
                details != null -> {
                    Text(details!!.overview.ifBlank { "No synopsis is available yet." }, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(900.dp))
                    if (details!!.cast.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("CAST", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                        Text(details!!.cast.take(8).joinToString(" • ") { it.name }, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            playError?.let { Text(it, color = AstraWaveColors.Warning, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AstraWaveSecondaryButton("Back", onBack)
                Text(
                    "Episodes and deeper title information are under More.",
                    color = AstraWaveColors.TertiaryText,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
