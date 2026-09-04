package com.astrawave.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.TitleDetailsActivity
import com.astrawave.app.core.LibraryItemRef
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.data.AstraWaveMetadataGateway
import com.astrawave.app.data.LocalLibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface HomeDiscoveryState {
    data object Loading : HomeDiscoveryState
    data class Ready(
        val movies: List<AstraWaveMetadataGateway.Item>,
        val series: List<AstraWaveMetadataGateway.Item>,
    ) : HomeDiscoveryState
    data class Error(val message: String) : HomeDiscoveryState
}

@Composable
fun PremiumHomeScreen(profileId: String = "default") {
    val context = LocalContext.current
    val library = remember { LocalLibraryStore(context) }
    val metadata = remember { AstraWaveMetadataGateway() }
    var libraryRefresh by remember { mutableStateOf(0) }
    var discovery by remember { mutableStateOf<HomeDiscoveryState>(HomeDiscoveryState.Loading) }

    val continueWatching = remember(profileId, libraryRefresh) { library.continueWatching(profileId).take(20) }
    val watchlist = remember(profileId, libraryRefresh) { library.watchlist(profileId).take(24) }
    val recent = remember(profileId, libraryRefresh) { library.history(profileId).take(24) }

    LaunchedEffect(Unit) {
        discovery = HomeDiscoveryState.Loading
        discovery = try {
            val pair = withContext(Dispatchers.IO) {
                metadata.load(AstraWaveMetadataGateway.Catalog.TRENDING_MOVIES).take(18) to
                    metadata.load(AstraWaveMetadataGateway.Catalog.TRENDING_SERIES).take(18)
            }
            HomeDiscoveryState.Ready(pair.first, pair.second)
        } catch (error: Exception) {
            HomeDiscoveryState.Error(error.message ?: "Discovery is temporarily unavailable")
        }
    }

    fun openItem(item: LibraryItemRef) {
        context.startActivity(
            Intent(context, TitleDetailsActivity::class.java)
                .putExtra(TitleDetailsActivity.EXTRA_TITLE, item.title)
                .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, item.type.name)
                .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, item.sourceId),
        )
    }

    fun openMetadata(item: AstraWaveMetadataGateway.Item) {
        val mediaType = if (item.type.equals("series", true) || item.type.equals("tv", true)) {
            LibraryMediaType.SERIES
        } else {
            LibraryMediaType.MOVIE
        }
        val sourceId = when {
            item.id.startsWith("tt", true) -> "stremio:cinemeta:${if (mediaType == LibraryMediaType.SERIES) "series" else "movie"}:${item.id}"
            item.id.toLongOrNull() != null -> "tmdb:${item.id}"
            else -> null
        }
        context.startActivity(
            Intent(context, TitleDetailsActivity::class.java)
                .putExtra(TitleDetailsActivity.EXTRA_TITLE, item.name)
                .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, mediaType.name)
                .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, sourceId),
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Text("ASTRAWAVE", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Text("Everything you watch and listen to.", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(7.dp))
        Text(
            "Pick up where you left off, jump into your list, or discover something new without setup friction.",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(28.dp))

        if (continueWatching.isNotEmpty()) {
            HomeSectionTitle("Continue Watching", "Resume across primary and backup playback sources")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                continueWatching.forEach { progress ->
                    val ratio = if (progress.durationMs > 0L) {
                        (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    AstraWaveFocusableCard(Modifier.width(220.dp).clickable { openItem(progress.item) }) {
                        Column {
                            AstraWaveArtwork(title = progress.item.title, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(9.dp))
                            Text(progress.item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(5.dp))
                            Text("${(ratio * 100).toInt()}% watched", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (watchlist.isNotEmpty()) {
            HomeSectionTitle("My Watchlist", "Saved for later")
            LibraryHomeRow(watchlist.map { it.item }, ::openItem)
            Spacer(Modifier.height(24.dp))
        }

        if (recent.isNotEmpty()) {
            HomeSectionTitle("Recently Watched", "Your latest playback history")
            LibraryHomeRow(recent.map { it.item }, ::openItem)
            Spacer(Modifier.height(24.dp))
        }

        when (val current = discovery) {
            HomeDiscoveryState.Loading -> AstraWaveStatePanel(
                "Building your home…",
                "Loading zero-config movie and TV discovery.",
                loading = true,
            )
            is HomeDiscoveryState.Error -> AstraWaveStatePanel(
                "Discovery is temporarily unavailable",
                current.message,
            )
            is HomeDiscoveryState.Ready -> {
                HomeSectionTitle("Trending Movies", "Fresh discovery from AstraWave metadata")
                MetadataHomeRow(current.movies, ::openMetadata)
                Spacer(Modifier.height(24.dp))
                HomeSectionTitle("Trending TV", "Series people are watching now")
                MetadataHomeRow(current.series, ::openMetadata)
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Home updates automatically as you watch, save, and revisit titles.",
            color = AstraWaveColors.TertiaryText,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clickable { libraryRefresh += 1 },
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun HomeSectionTitle(title: String, subtitle: String) {
    Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(3.dp))
    Text(subtitle, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun LibraryHomeRow(items: List<LibraryItemRef>, onOpen: (LibraryItemRef) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.distinctBy { it.id }.forEach { item ->
            AstraWaveFocusableCard(Modifier.width(190.dp).clickable { onOpen(item) }) {
                Column {
                    AstraWaveArtwork(title = item.title, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(9.dp))
                    Text(item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (item.type == LibraryMediaType.EPISODE) "Continue episode" else "Open details",
                        color = AstraWaveColors.Accent,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataHomeRow(
    items: List<AstraWaveMetadataGateway.Item>,
    onOpen: (AstraWaveMetadataGateway.Item) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { item ->
            AstraWaveFocusableCard(Modifier.width(190.dp).clickable { onOpen(item) }) {
                Column {
                    AstraWaveArtwork(title = item.name, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(9.dp))
                    Text(item.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    item.releaseInfo?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        item.description ?: "Open for details and watch options.",
                        color = AstraWaveColors.SecondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                    )
                }
            }
        }
    }
}
