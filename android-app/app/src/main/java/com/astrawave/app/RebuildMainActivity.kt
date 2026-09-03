package com.astrawave.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.AccountOverview
import com.astrawave.app.data.AppSettingsStore
import com.astrawave.app.data.AstraWaveCatalog
import com.astrawave.app.data.IptvSourceStore
import com.astrawave.app.data.TmdbCatalogPage
import com.astrawave.app.data.TmdbCatalogRepository
import com.astrawave.app.ui.AstraWaveColors
import com.astrawave.app.ui.AstraWaveFocusableCard
import com.astrawave.app.ui.AstraWaveGuideScreen
import com.astrawave.app.ui.AstraWaveSportsScreen
import com.astrawave.app.ui.AstraWaveTheme
import com.astrawave.app.ui.AudioLibraryScreen
import com.astrawave.app.ui.LiveTvHubScreen
import com.astrawave.app.ui.MyAstraWaveHub
import com.astrawave.app.ui.UniversalSearchScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RebuildMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AstraWaveTheme {
                Surface(color = AstraWaveColors.Background) { RebuildRoot() }
            }
        }
    }
}

private enum class RebuildDestination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Movies("Movies", Icons.Default.Movie),
    Shows("TV", Icons.Default.Tv),
    Live("Live TV", Icons.Default.LiveTv),
    Guide("Guide", Icons.Default.CalendarMonth),
    Sports("Sports", Icons.Default.SportsFootball),
    Audio("Music & Podcasts", Icons.Default.MusicNote),
    Discover("Discover", Icons.Default.Explore),
    Search("Search", Icons.Default.Search),
    My("My AstraWave", Icons.Default.AccountCircle),
}

private sealed interface CatalogLoadState {
    data object Loading : CatalogLoadState
    data class Ready(val page: TmdbCatalogPage) : CatalogLoadState
    data class Error(val message: String) : CatalogLoadState
}

@Composable
private fun RebuildRoot() {
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    val context = LocalContext.current
    val activeProfileId = "default"
    var iptvSources by remember(activeProfileId) { mutableStateOf(IptvSourceStore(context).load(activeProfileId)) }
    var current by remember { mutableStateOf(RebuildDestination.Home) }

    Row(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        if (wide) {
            NavigationRail(containerColor = AstraWaveColors.BackgroundRaised, modifier = Modifier.width(108.dp)) {
                Spacer(Modifier.height(14.dp))
                Text("AW", color = AstraWaveColors.Accent, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp))
                RebuildDestination.entries.forEach { item ->
                    NavigationRailItem(
                        selected = current == item,
                        onClick = { current = item },
                        icon = { Icon(item.icon, item.label) },
                        label = { Text(item.label, maxLines = 1) },
                    )
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (!wide) SectionStrip(current) { current = it }
            when (current) {
                RebuildDestination.Home -> CatalogLanding("Home", movieCatalogs.take(3) + tvCatalogs.take(3), showIntro = true)
                RebuildDestination.Movies -> CatalogLanding("Movies", movieCatalogs)
                RebuildDestination.Shows -> CatalogLanding("TV Shows", tvCatalogs)
                RebuildDestination.Live -> LiveTvHubScreen(
                    sources = iptvSources,
                    onSourcesChanged = { iptvSources = it },
                )
                RebuildDestination.Guide -> AstraWaveGuideScreen(sources = iptvSources)
                RebuildDestination.Sports -> AstraWaveSportsScreen(sources = iptvSources)
                RebuildDestination.Audio -> AudioLibraryScreen(subscriptions = emptyList(), stations = emptyList())
                RebuildDestination.Discover -> CatalogLanding("Discover", movieCatalogs + tvCatalogs)
                RebuildDestination.Search -> UniversalSearchScreen()
                RebuildDestination.My -> MyAstraWaveHub(
                    account = AccountOverview(
                        userId = "local",
                        displayName = "AstraWave User",
                        activeProfileId = activeProfileId,
                        planName = "AstraWave Free",
                        cloudSyncEnabled = false,
                    ),
                    lists = emptyList(),
                )
            }

            if (!wide) {
                NavigationBar(containerColor = AstraWaveColors.BackgroundRaised) {
                    listOf(RebuildDestination.Home, RebuildDestination.Movies, RebuildDestination.Live, RebuildDestination.Sports, RebuildDestination.My).forEach { item ->
                        NavigationBarItem(
                            selected = current == item,
                            onClick = { current = item },
                            icon = { Icon(item.icon, item.label) },
                            label = { Text(item.label, maxLines = 1) },
                        )
                    }
                }
            }
        }
    }
}

private val movieCatalogs = listOf(
    AstraWaveCatalog.TRENDING_MOVIES,
    AstraWaveCatalog.POPULAR_MOVIES,
    AstraWaveCatalog.NOW_PLAYING_MOVIES,
    AstraWaveCatalog.TOP_RATED_MOVIES,
    AstraWaveCatalog.UPCOMING_MOVIES,
)

private val tvCatalogs = listOf(
    AstraWaveCatalog.TRENDING_TV,
    AstraWaveCatalog.POPULAR_TV,
    AstraWaveCatalog.AIRING_TODAY,
    AstraWaveCatalog.ON_THE_AIR,
    AstraWaveCatalog.TOP_RATED_TV,
)

@Composable
private fun SectionStrip(current: RebuildDestination, onSelect: (RebuildDestination) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(AstraWaveColors.BackgroundRaised).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RebuildDestination.entries.forEach { item ->
            FilterChip(
                selected = current == item,
                onClick = { onSelect(item) },
                label = { Text(item.label) },
                leadingIcon = { Icon(item.icon, null) },
            )
        }
    }
}

@Composable
private fun CatalogLanding(title: String, catalogs: List<AstraWaveCatalog>, showIntro: Boolean = false) {
    val context = LocalContext.current
    val token = remember { AppSettingsStore(context).effectiveTmdbBearerToken() }
    val repository = remember(token) { TmdbCatalogRepository(token) }
    val states = remember(catalogs, token) {
        mutableStateMapOf<AstraWaveCatalog, CatalogLoadState>().apply {
            catalogs.forEach { put(it, CatalogLoadState.Loading) }
        }
    }

    LaunchedEffect(catalogs, token) {
        if (!repository.isConfigured()) return@LaunchedEffect
        catalogs.forEach { catalog ->
            states[catalog] = CatalogLoadState.Loading
            states[catalog] = try {
                val page = withContext(Dispatchers.IO) { repository.load(catalog) }
                CatalogLoadState.Ready(page)
            } catch (error: Exception) {
                CatalogLoadState.Error(error.message ?: "Unable to load catalog")
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        if (showIntro) {
            Text("ASTRAWAVE", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(10.dp))
            Text("Everything you watch and listen to.", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(10.dp))
            Text(
                "Movies, TV, live channels, sports, music and podcasts in one clean, personalized home.",
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(34.dp))
        } else {
            Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(6.dp))
            Text("Built-in AstraWave discovery powered by TMDB metadata", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(22.dp))
        }

        if (!repository.isConfigured()) {
            ConfigurationCard(
                title = "TMDB setup needed",
                message = "Connect a TMDB bearer token in My AstraWave → Account → Integrations to populate built-in movie and TV discovery. The app stays usable instead of showing a blank page.",
            )
            return@Column
        }

        catalogs.forEach { catalog ->
            when (val state = states[catalog] ?: CatalogLoadState.Loading) {
                CatalogLoadState.Loading -> LoadingCatalogRow(catalog)
                is CatalogLoadState.Ready -> RealCatalogRow(state.page)
                is CatalogLoadState.Error -> ErrorCatalogRow(catalog, state.message)
            }
        }
    }
}

@Composable
private fun RealCatalogRow(page: TmdbCatalogPage) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(page.catalog.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(9.dp))
        if (page.items.isEmpty()) {
            ConfigurationCard("Nothing to show yet", "TMDB returned no items for this catalog.")
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                page.items.take(12).forEach { item ->
                    AstraWaveFocusableCard(Modifier.width(168.dp)) {
                        Column {
                            Text(item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                item.overview.ifBlank { "Open for details and Watch options." },
                                color = AstraWaveColors.SecondaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 4,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun LoadingCatalogRow(catalog: AstraWaveCatalog) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp).background(AstraWaveColors.Surface, MaterialTheme.shapes.medium).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.width(22.dp), color = AstraWaveColors.Accent, strokeWidth = 2.dp)
        Column {
            Text(catalog.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            Text("Loading real catalog data…", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorCatalogRow(catalog: AstraWaveCatalog, message: String) {
    ConfigurationCard(catalog.title, "Could not refresh this row: $message")
}

@Composable
private fun ConfigurationCard(title: String, message: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 7.dp).background(AstraWaveColors.Surface, MaterialTheme.shapes.medium).padding(18.dp),
    ) {
        Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(5.dp))
        Text(message, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
    }
}
