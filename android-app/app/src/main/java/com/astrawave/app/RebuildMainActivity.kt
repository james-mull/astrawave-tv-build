package com.astrawave.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.AccountOverview
import com.astrawave.app.core.AccountSection
import com.astrawave.app.core.MultiviewLayout
import com.astrawave.app.core.MultiviewPane
import com.astrawave.app.core.MultiviewSession
import com.astrawave.app.data.AppSettingsStore
import com.astrawave.app.data.AstraWaveCatalog
import com.astrawave.app.data.IptvSourceStore
import com.astrawave.app.data.StremioCatalogAggregator
import com.astrawave.app.data.StremioCatalogRow
import com.astrawave.app.data.TmdbCatalogPage
import com.astrawave.app.data.TmdbCatalogRepository
import com.astrawave.app.ui.AstraWaveArtwork
import com.astrawave.app.ui.AstraWaveColors
import com.astrawave.app.ui.AstraWaveFocusableCard
import com.astrawave.app.ui.AstraWaveGuideScreen
import com.astrawave.app.ui.AstraWaveNavigationContract
import com.astrawave.app.ui.AstraWaveSportsScreen
import com.astrawave.app.ui.AstraWaveTheme
import com.astrawave.app.ui.AudioLibraryScreen
import com.astrawave.app.ui.LibraryActionRow
import com.astrawave.app.ui.LiveTvHubScreen
import com.astrawave.app.ui.MultiviewScreen
import com.astrawave.app.ui.MyAstraWaveHub
import com.astrawave.app.ui.PersonalMediaScreen
import com.astrawave.app.ui.StremioAddonScreen
import com.astrawave.app.ui.UniversalSearchScreen
import com.astrawave.app.ui.toLibraryItemRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

private enum class RebuildDestination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Default.Home),
    Movies("movies", "Movies", Icons.Default.Movie),
    Shows("tv", "TV", Icons.Default.Tv),
    Live("live", "Live TV", Icons.Default.LiveTv),
    Guide("guide", "Guide", Icons.Default.CalendarMonth),
    Sports("sports", "Sports", Icons.Default.SportsFootball),
    Multiview("multiview", "Multiview", Icons.Default.Tv),
    Audio("audio", "Music & Podcasts", Icons.Default.MusicNote),
    PersonalMedia("personal-media", "Personal Media", Icons.Default.Tv),
    Addons("addons", "Addons", Icons.Default.Explore),
    Discover("discover", "Discover", Icons.Default.Explore),
    Search("search", "Search", Icons.Default.Search),
    My("my", "My AstraWave", Icons.Default.AccountCircle),
}

private sealed interface CatalogLoadState {
    data object Loading : CatalogLoadState
    data class Ready(val page: TmdbCatalogPage) : CatalogLoadState
    data class Error(val message: String) : CatalogLoadState
}

private sealed interface AddonCatalogLoadState {
    data object Loading : AddonCatalogLoadState
    data class Ready(val rows: List<StremioCatalogRow>) : AddonCatalogLoadState
}

@Composable
private fun RebuildRoot() {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val useRail = isTv || configuration.screenWidthDp >= 840
    val activeProfileId = "default"
    val primaryDestinations = remember(isTv) {
        val contract = if (isTv) AstraWaveNavigationContract.tv else AstraWaveNavigationContract.mobileTablet
        contract.mapNotNull { nav -> RebuildDestination.entries.firstOrNull { it.route == nav.route } }
    }
    val tvRailFocusScope = rememberCoroutineScope()
    var tvRailExpanded by remember(isTv) { mutableStateOf(!isTv) }
    var tvRailCollapseJob by remember(isTv) { mutableStateOf<Job?>(null) }
    var iptvSources by remember(activeProfileId) { mutableStateOf(IptvSourceStore(context).load(activeProfileId)) }
    var current by remember { mutableStateOf(RebuildDestination.Home) }
    var multiviewPanes by remember { mutableStateOf<List<MultiviewPane>>(emptyList()) }
    var multiviewAudioPaneId by remember { mutableStateOf<String?>(null) }

    fun handleTvRailFocus(hasFocus: Boolean) {
        if (!isTv) return
        tvRailCollapseJob?.cancel()
        tvRailCollapseJob = null
        if (hasFocus) {
            tvRailExpanded = true
        } else {
            tvRailCollapseJob = tvRailFocusScope.launch {
                delay(120)
                tvRailExpanded = false
            }
        }
    }

    fun addToMultiview(pane: MultiviewPane) {
        if (multiviewPanes.any { it.streamUrl == pane.streamUrl }) {
            Toast.makeText(context, "Already in Multiview.", Toast.LENGTH_SHORT).show()
            return
        }
        if (multiviewPanes.size >= 4) {
            Toast.makeText(context, "Multiview supports up to four streams.", Toast.LENGTH_SHORT).show()
            return
        }
        multiviewPanes = multiviewPanes + pane.copy(muted = multiviewPanes.isNotEmpty())
        if (multiviewAudioPaneId == null) multiviewAudioPaneId = pane.id
    }

    fun openMultiview() {
        if (multiviewPanes.isNotEmpty()) current = RebuildDestination.Multiview
    }

    Row(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        if (useRail) {
            val railWidth = when {
                isTv && tvRailExpanded -> 216.dp
                isTv -> 72.dp
                else -> 108.dp
            }
            NavigationRail(containerColor = AstraWaveColors.BackgroundRaised, modifier = Modifier.width(railWidth)) {
                Spacer(Modifier.height(14.dp))
                Text(
                    if (isTv && !tvRailExpanded) "AW" else "ASTRAWAVE",
                    color = AstraWaveColors.Accent,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                )
                Spacer(Modifier.height(10.dp))
                primaryDestinations.forEach { item ->
                    val showRailLabel = !isTv || tvRailExpanded
                    NavigationRailItem(
                        selected = current == item,
                        onClick = { current = item },
                        modifier = Modifier.onFocusChanged { state -> handleTvRailFocus(state.hasFocus) },
                        icon = { Icon(item.icon, item.label) },
                        label = if (showRailLabel) ({ Text(item.label, maxLines = 1) }) else null,
                        alwaysShowLabel = showRailLabel,
                    )
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (!useRail) SectionStrip(current, primaryDestinations) { current = it }
            when (current) {
                RebuildDestination.Home -> CatalogLanding("Home", movieCatalogs.take(3) + tvCatalogs.take(3), showIntro = true)
                RebuildDestination.Movies -> CatalogLanding("Movies", movieCatalogs)
                RebuildDestination.Shows -> CatalogLanding("TV Shows", tvCatalogs)
                RebuildDestination.Live -> LiveTvHubScreen(
                    sources = iptvSources,
                    onSourcesChanged = { iptvSources = it },
                    multiviewCount = multiviewPanes.size,
                    onAddToMultiview = ::addToMultiview,
                    onOpenMultiview = ::openMultiview,
                )
                RebuildDestination.Guide -> AstraWaveGuideScreen(sources = iptvSources)
                RebuildDestination.Sports -> AstraWaveSportsScreen(
                    sources = iptvSources,
                    multiviewCount = multiviewPanes.size,
                    onAddToMultiview = ::addToMultiview,
                    onOpenMultiview = ::openMultiview,
                )
                RebuildDestination.Multiview -> {
                    if (multiviewPanes.isEmpty()) {
                        ConfigurationCard("Multiview is empty", "Add live channels or matched sports streams from Live TV or Sports.")
                    } else {
                        val layout = when (multiviewPanes.size) {
                            1, 2 -> MultiviewLayout.TWO_UP
                            3 -> MultiviewLayout.THREE_UP
                            else -> MultiviewLayout.FOUR_UP
                        }
                        MultiviewScreen(
                            session = MultiviewSession(
                                id = "active",
                                layout = layout,
                                panes = multiviewPanes,
                                activeAudioPaneId = multiviewAudioPaneId ?: multiviewPanes.first().id,
                            ),
                            onActivateAudio = { multiviewAudioPaneId = it },
                            onOpenPane = { pane ->
                                context.startActivity(Intent(context, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_URL, pane.streamUrl))
                            },
                            onReplacePane = { pane ->
                                multiviewPanes = multiviewPanes.filterNot { it.id == pane.id }
                                if (multiviewAudioPaneId == pane.id) multiviewAudioPaneId = multiviewPanes.firstOrNull()?.id
                                current = RebuildDestination.Live
                            },
                        )
                    }
                }
                RebuildDestination.Audio -> AudioLibraryScreen(profileId = activeProfileId)
                RebuildDestination.PersonalMedia -> PersonalMediaScreen(profileId = activeProfileId)
                RebuildDestination.Addons -> StremioAddonScreen(profileId = activeProfileId)
                RebuildDestination.Discover -> CombinedDiscoverScreen(profileId = activeProfileId)
                RebuildDestination.Search -> UniversalSearchScreen(profileId = activeProfileId)
                RebuildDestination.My -> MyAstraWaveHub(
                    account = AccountOverview(
                        userId = "local",
                        displayName = "AstraWave User",
                        activeProfileId = activeProfileId,
                        planName = "AstraWave Free",
                        cloudSyncEnabled = false,
                    ),
                    lists = emptyList(),
                    onOpenAccountSection = { section ->
                        current = when (section) {
                            AccountSection.IPTV -> RebuildDestination.Live
                            AccountSection.PERSONAL_MEDIA -> RebuildDestination.PersonalMedia
                            AccountSection.ADDONS -> RebuildDestination.Addons
                            AccountSection.SPORTS -> RebuildDestination.Sports
                            else -> current
                        }
                    },
                )
            }

            if (!useRail) {
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
private fun SectionStrip(
    current: RebuildDestination,
    items: List<RebuildDestination>,
    onSelect: (RebuildDestination) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(AstraWaveColors.BackgroundRaised).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
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
private fun CombinedDiscoverScreen(profileId: String) {
    val context = LocalContext.current
    val token = remember { AppSettingsStore(context).effectiveTmdbBearerToken() }
    val tmdb = remember(token) { TmdbCatalogRepository(token) }
    val addonAggregator = remember { StremioCatalogAggregator(context) }
    val catalogs = movieCatalogs + tvCatalogs
    val tmdbStates = remember(catalogs, token) {
        mutableStateMapOf<AstraWaveCatalog, CatalogLoadState>().apply {
            catalogs.forEach { put(it, CatalogLoadState.Loading) }
        }
    }
    var addonState by remember(profileId) { mutableStateOf<AddonCatalogLoadState>(AddonCatalogLoadState.Loading) }

    LaunchedEffect(catalogs, token, profileId) {
        if (tmdb.isConfigured()) {
            catalogs.forEach { catalog ->
                tmdbStates[catalog] = try {
                    CatalogLoadState.Ready(withContext(Dispatchers.IO) { tmdb.load(catalog) })
                } catch (error: Exception) {
                    CatalogLoadState.Error(error.message ?: "Unable to load catalog")
                }
            }
        }
        addonState = AddonCatalogLoadState.Ready(
            withContext(Dispatchers.IO) { addonAggregator.load(profileId = profileId, maxItemsPerCatalog = 16) },
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Discover", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "AstraWave discovery combines built-in TMDB metadata with catalogs from your enabled compatible addons.",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(22.dp))

        if (tmdb.isConfigured()) {
            catalogs.forEach { catalog ->
                when (val state = tmdbStates[catalog] ?: CatalogLoadState.Loading) {
                    CatalogLoadState.Loading -> LoadingCatalogRow(catalog)
                    is CatalogLoadState.Ready -> RealCatalogRow(state.page)
                    is CatalogLoadState.Error -> ErrorCatalogRow(catalog, state.message)
                }
            }
        } else {
            ConfigurationCard("TMDB setup needed", "TMDB rows are unavailable, but compatible enabled addon catalogs can still appear below.")
            Spacer(Modifier.height(18.dp))
        }

        when (val state = addonState) {
            AddonCatalogLoadState.Loading -> ConfigurationCard("Loading addon catalogs…", "Refreshing metadata from your enabled compatible addons.")
            is AddonCatalogLoadState.Ready -> {
                if (state.rows.isNotEmpty()) {
                    Text("From Your Addons", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                }
                state.rows.forEach { row -> AddonDiscoverRow(row) }
            }
        }
    }
}

@Composable
private fun AddonDiscoverRow(row: StremioCatalogRow) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(row.catalog.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
        Text("${row.addonName} • ${row.catalog.type}", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(9.dp))
        if (row.error != null) {
            ConfigurationCard("${row.catalog.name} unavailable", row.error)
        } else if (row.items.isEmpty()) {
            ConfigurationCard("Nothing to show", "This addon catalog returned no metadata items.")
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.items.forEach { item ->
                    AstraWaveFocusableCard(Modifier.width(184.dp)) {
                        Column {
                            AstraWaveArtwork(title = item.name, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp))
                            Text(item.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            item.releaseInfo?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                item.description ?: "Metadata from ${row.addonName}. Playback requires an eligible authorized source.",
                                color = AstraWaveColors.SecondaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
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
                    AstraWaveFocusableCard(Modifier.width(184.dp)) {
                        Column {
                            AstraWaveArtwork(title = item.title, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp))
                            Text(item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                item.overview.ifBlank { "Open for details and Watch options." },
                                color = AstraWaveColors.SecondaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                            )
                            Spacer(Modifier.height(10.dp))
                            LibraryActionRow(item = item.toLibraryItemRef(), profileId = "default")
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
