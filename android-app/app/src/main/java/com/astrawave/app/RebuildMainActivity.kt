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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.AccountOverview
import com.astrawave.app.core.AstraWaveList
import com.astrawave.app.data.AstraWaveCatalog
import com.astrawave.app.ui.AstraWaveColors
import com.astrawave.app.ui.AstraWaveTheme
import com.astrawave.app.ui.MyAstraWaveHub

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

@Composable
private fun RebuildRoot() {
    val wide = LocalConfiguration.current.screenWidthDp >= 840
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
                RebuildDestination.Home -> LandingScreen()
                RebuildDestination.Movies -> CatalogLanding("Movies", movieCatalogs)
                RebuildDestination.Shows -> CatalogLanding("TV Shows", tvCatalogs)
                RebuildDestination.Live -> PlaceholderScreen("Live TV", "AstraWave Free TV, My IPTV and Combined mode")
                RebuildDestination.Guide -> PlaceholderScreen("Guide", "Fast merged EPG with TV-first navigation")
                RebuildDestination.Sports -> PlaceholderScreen("Sports", "Schedules, broadcaster matching and Watch actions")
                RebuildDestination.Audio -> PlaceholderScreen("Music & Podcasts", "Music, podcasts, video podcasts and radio")
                RebuildDestination.Discover -> CatalogLanding("Discover", movieCatalogs + tvCatalogs)
                RebuildDestination.Search -> PlaceholderScreen("Search", "Universal search across every AstraWave source")
                RebuildDestination.My -> MyAstraWaveHub(
                    account = AccountOverview(
                        userId = "local",
                        displayName = "AstraWave User",
                        activeProfileId = "default",
                        planName = "AstraWave Free",
                        cloudSyncEnabled = false,
                    ),
                    lists = listOf(
                        AstraWaveList("family-night", "default", "Family Night", "Movies everyone can agree on", isPinned = true),
                        AstraWaveList("mind-benders", "default", "Mind-Benders", "Thrillers, sci-fi and mysteries"),
                    ),
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
private fun LandingScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
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
        Text("Built-in discovery", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        (movieCatalogs.take(3) + tvCatalogs.take(3)).forEach { catalog -> CatalogRow(catalog) }
    }
}

@Composable
private fun CatalogLanding(title: String, catalogs: List<AstraWaveCatalog>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(6.dp))
        Text("AstraWave built-in TMDB catalog structure", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(22.dp))
        catalogs.forEach { CatalogRow(it) }
    }
}

@Composable
private fun CatalogRow(catalog: AstraWaveCatalog) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).background(AstraWaveColors.Surface, MaterialTheme.shapes.medium).padding(18.dp),
    ) {
        Text(catalog.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
        Text("${catalog.mediaType.uppercase()} • ${catalog.endpoint}", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyLarge)
    }
}
