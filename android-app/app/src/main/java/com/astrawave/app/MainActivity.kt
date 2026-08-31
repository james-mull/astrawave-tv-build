package com.astrawave.app

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astrawave.app.core.DemoSources

private val Bg = Color(0xFF080A0F)
private val Panel = Color(0xFF141924)
private val PanelAlt = Color(0xFF202736)
private val Primary = Color(0xFFF7F8FB)
private val Muted = Color(0xFFA7AEBB)
private val Accent = Color(0xFF8B5CF6)
private val Success = Color(0xFF39D98A)
private val LiveRed = Color(0xFFE84A5F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) {
                Surface(color = Bg) { AstraWaveApp() }
            }
        }
    }
}

enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Movies("Movies", Icons.Default.Movie),
    Shows("TV Shows", Icons.Default.Tv),
    Live("Live TV", Icons.Default.LiveTv),
    Guide("Guide", Icons.Default.CalendarMonth),
    Sports("Sports", Icons.Default.SportsFootball),
    Audio("Music & Podcasts", Icons.Default.MusicNote),
    Discover("Discover", Icons.Default.Explore),
    Search("Search", Icons.Default.Search),
    My("My AstraWave", Icons.Default.AccountCircle)
}

@Composable
fun AstraWaveApp() {
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    var current by remember { mutableStateOf(Destination.Home) }

    Row(Modifier.fillMaxSize().background(Bg)) {
        if (wide) {
            NavigationRail(containerColor = Color(0xFF0D1016), modifier = Modifier.width(104.dp)) {
                Spacer(Modifier.height(14.dp))
                Text("AW", color = Accent, fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 12.dp))
                Destination.entries.forEach { item ->
                    NavigationRailItem(
                        selected = current == item,
                        onClick = { current = item },
                        icon = { Icon(item.icon, item.label) },
                        label = { Text(item.label, fontSize = 9.sp, maxLines = 1) }
                    )
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (!wide) MobileSectionStrip(current) { current = it }
            Screen(current, Modifier.weight(1f))
            if (!wide) {
                NavigationBar(containerColor = Color(0xFF0D1016)) {
                    listOf(Destination.Home, Destination.Movies, Destination.Live, Destination.Sports, Destination.My).forEach { item ->
                        NavigationBarItem(
                            selected = current == item,
                            onClick = { current = item },
                            icon = { Icon(item.icon, item.label) },
                            label = { Text(item.label, maxLines = 1) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileSectionStrip(current: Destination, onSelect: (Destination) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Color(0xFF0D1016)).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Destination.entries.forEach { item ->
            FilterChip(
                selected = current == item,
                onClick = { onSelect(item) },
                label = { Text(item.label, maxLines = 1) },
                leadingIcon = { Icon(item.icon, null, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}

@Composable
private fun Screen(destination: Destination, modifier: Modifier) {
    Box(modifier.fillMaxSize()) {
        when (destination) {
            Destination.Home -> HomeScreen()
            Destination.Movies -> CatalogScreen("Movies", listOf("Trending Now", "New Releases", "Top Rated", "Free on AstraWave", "Action", "Comedy", "Thriller", "Sci-Fi", "Family", "Hidden Gems"))
            Destination.Shows -> CatalogScreen("TV Shows", listOf("Trending", "Up Next", "New Episodes", "Airing Today", "Binge Worthy", "Networks", "Reality", "Documentary", "Kids", "My Shows"))
            Destination.Live -> LiveTvScreen()
            Destination.Guide -> GuideScreen()
            Destination.Sports -> SportsScreen()
            Destination.Audio -> AudioScreen()
            Destination.Discover -> DiscoverScreen()
            Destination.Search -> SearchScreen()
            Destination.My -> MyAstraWaveScreen()
        }
    }
}

@Composable
private fun HomeScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 30.dp)) {
        Hero()
        MediaRow("Continue Watching", listOf("Night Drive", "Northbound", "Signal", "Afterlight"))
        SportsStrip()
        MediaRow("Live Now", listOf("Local News", "Weather Live", "Music Live", "World News"), "LIVE")
        MediaRow("Trending Movies", listOf("Orbit", "The Last Ridge", "Echo City", "Beyond Midnight", "Static"))
        MediaRow("New Episodes", listOf("Frontier S2:E4", "Signal S1:E8", "Deep Water S3:E2", "Afterlight S1:E5"))
        MediaRow("Continue Listening", listOf("Daily Brief", "Road Mix", "Tech Weekly", "True Crime Daily"))
        MediaRow("Watch Tonight", listOf("Family Pick", "Top Movie", "Big Game", "Hidden Gem"))
    }
}

@Composable
private fun Hero() {
    Box(
        Modifier.fillMaxWidth().height(340.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFF171C29), Color(0xFF2B173D), Bg)))
            .padding(28.dp)
    ) {
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.88f)) {
            Text("ASTRAWAVE", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("Everything you watch and listen to.", color = Primary, fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 38.sp)
            Spacer(Modifier.height(10.dp))
            Text("Movies, TV, live channels, sports, music and podcasts — one fast, personalized home.", color = Muted, fontSize = 15.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("▶ Play Featured", Accent)
                Pill("＋ My List", PanelAlt)
            }
        }
    }
}

@Composable
private fun CatalogScreen(title: String, sections: List<String>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        PageHeader(title, "Browse everything in one clean catalog")
        sections.forEachIndexed { index, section ->
            MediaRow(section, demoTitles(index))
        }
    }
}

private fun demoTitles(seed: Int): List<String> = when (seed % 4) {
    0 -> listOf("Orbit", "Northbound", "Afterlight", "Signal", "Static")
    1 -> listOf("The Last Ridge", "Echo City", "Deep Water", "Frontier", "Night Drive")
    2 -> listOf("Atlas", "Highline", "Zero Hour", "Red Sky", "Parallel")
    else -> listOf("Momentum", "Open Water", "Threshold", "The Run", "Vantage")
}

@Composable
private fun LiveTvScreen() {
    var source by remember { mutableStateOf("Combined") }
    val sourceOptions = listOf("Combined", "AstraWave Free", "My M3U", "Xtream Codes")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        PageHeader("Live TV", "Fast channels, smart source merging and automatic fallback")
        Text("SOURCE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sourceOptions.forEach { item -> FilterChip(selected = source == item, onClick = { source = item }, label = { Text(item) }) }
        }
        StatusCard("AstraWave Free", "842 healthy channels", Success)
        MediaRow("Recently Watched", listOf("CBS Local", "LiveNOW", "Weather", "Sports Central", "News 24"), "LIVE")
        MediaRow("Favorites", listOf("Local 7", "Movie Channel", "Kids TV", "World News", "Music TV"), "LIVE")
        MediaRow("Sports", listOf("Sports Central", "College Sports", "Racing", "Football", "Fight Night"), "LIVE")
        MediaRow("News & Local", listOf("Local News", "National News", "Weather", "Business", "World News"), "LIVE")
        MediaRow("Entertainment", listOf("Comedy TV", "Classic TV", "Reality", "Movies", "Lifestyle"), "LIVE")
    }
}

@Composable
private fun GuideScreen() {
    val channels = listOf(
        Triple("CBS Local", "Morning News", "NFL Today"),
        Triple("NBC Local", "Today", "The Voice"),
        Triple("FOX Local", "Local News", "Baseball"),
        Triple("ABC Local", "Good Morning", "20/20"),
        Triple("Sports Central", "Live Pregame", "Football Live")
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        PageHeader("TV Guide", "A clean, merged guide across AstraWave and your playlists")
        Row(Modifier.padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Now", "Tonight", "Tomorrow", "Sports").forEachIndexed { i, label -> FilterChip(selected = i == 0, onClick = {}, label = { Text(label) }) }
        }
        Spacer(Modifier.height(14.dp))
        channels.forEach { (channel, now, next) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 5.dp).background(Panel, RoundedCornerShape(15.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(116.dp)) { Text(channel, color = Primary, fontWeight = FontWeight.Bold); Text("LIVE", color = LiveRed, fontSize = 10.sp, fontWeight = FontWeight.Black) }
                Column(Modifier.weight(1f)) { Text(now, color = Primary, fontWeight = FontWeight.SemiBold); Text("Now", color = Muted, fontSize = 11.sp) }
                Column(Modifier.weight(1f)) { Text(next, color = Muted); Text("Next", color = Muted.copy(alpha = .65f), fontSize = 11.sp) }
                Icon(Icons.Default.PlayArrow, null, tint = Accent)
            }
        }
    }
}

@Composable
private fun SportsScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        PageHeader("Sports", "Live now, starting soon, today and this week")
        SportsStrip()
        SectionTitle("Today")
        listOf(
            "NFL • Broncos vs Chiefs • 2:25 PM • CBS",
            "NBA • Nuggets vs Lakers • 6:00 PM • Sports Network",
            "MLB • Dodgers vs Padres • 7:10 PM • Baseball Network",
            "NHL • Avalanche vs Stars • 7:30 PM • Hockey Network"
        ).forEach { EventRow(it) }
        MediaRow("Leagues", listOf("NFL", "NBA", "MLB", "NHL", "NCAA", "Soccer", "UFC", "F1"))
    }
}

@Composable
private fun AudioScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        PageHeader("Music & Podcasts", "Music, podcasts, video podcasts and radio")
        MediaRow("Continue Listening", listOf("Daily Brief", "Tech Weekly", "True Crime Daily", "Road Mix"))
        MediaRow("Made For You", listOf("Focus Mix", "Road Trip", "Chill", "Workout", "Throwbacks"))
        MediaRow("Video Podcasts", listOf("The Interview", "Sports Desk", "Tech Talk", "Longform"), "VIDEO")
        MediaRow("Radio", listOf("Local Radio", "Sports Radio", "News Radio", "Classic Rock", "International"), "LIVE")
    }
}

@Composable
private fun DiscoverScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        PageHeader("Discover", "Find something great without endless scrolling")
        MediaRow("Watch Tonight", listOf("Best Movie", "Big Game", "New Episode", "Family Pick", "Something Different"))
        MediaRow("By Mood", listOf("Funny", "Intense", "Feel Good", "Mind-Bending", "Easy Watch"))
        MediaRow("Quick Picks", listOf("Under 90 Min", "One Episode", "Live Now", "Podcast", "Music Mix"))
    }
}

@Composable
private fun SearchScreen() {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        PageHeader("Search", "Movies, shows, channels, sports, music and podcasts")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search all of AstraWave") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        Text(if (query.isBlank()) "Popular searches" else "Results for “$query”", color = Primary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        MediaRow("Across AstraWave", listOf("Movies", "TV Shows", "Live Channels", "Sports", "Music", "Podcasts", "People", "Collections"))
    }
}

@Composable
private fun MyAstraWaveScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        PageHeader("My AstraWave", "Your entertainment, profiles, sources and settings")
        MediaRow("Your Library", listOf("Continue Watching", "Watchlist", "Continue Listening", "Favorite Channels", "Favorite Teams"))
        SectionTitle("Sources")
        DemoSources.sources.forEach { source ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 5.dp).background(Panel, RoundedCornerShape(15.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (source.enabled) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline, null, tint = if (source.enabled) Success else Muted)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(source.name, color = Primary, fontWeight = FontWeight.Bold); Text(source.type.name.replace('_', ' '), color = Muted, fontSize = 11.sp) }
                if (source.uptimePercent != null) Text("${source.uptimePercent}%", color = Success, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }
        MediaRow("Manage", listOf("Profiles", "Extensions", "Playback", "Subtitles", "Notifications", "Diagnostics", "Backup & Restore"))
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
        Text(title, color = Primary, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun StatusCard(title: String, status: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp).background(Panel, RoundedCornerShape(15.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, RoundedCornerShape(99.dp)))
        Spacer(Modifier.width(10.dp))
        Text(title, color = Primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(status, color = color, fontSize = 12.sp)
    }
}

@Composable
private fun SportsStrip() {
    Column(Modifier.padding(top = 12.dp)) {
        SectionTitle("Sports Starting Soon")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SportsCard("NFL", "Broncos vs Chiefs", "2:25 PM")
            SportsCard("NBA", "Nuggets vs Lakers", "6:00 PM")
            SportsCard("MLB", "Dodgers vs Padres", "7:10 PM")
        }
    }
}

@Composable
private fun SportsCard(league: String, event: String, time: String) {
    Column(Modifier.width(270.dp).background(Panel, RoundedCornerShape(18.dp)).clickable { }.padding(18.dp)) {
        Row { Text(league, color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text(time, color = Muted, fontSize = 12.sp) }
        Spacer(Modifier.height(14.dp))
        Text(event, color = Primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("View event & watch options", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun EventRow(text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 5.dp).background(Panel, RoundedCornerShape(14.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Sports, null, tint = Accent)
        Spacer(Modifier.width(12.dp))
        Text(text, color = Primary, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun MediaRow(title: String, items: List<String>, badge: String? = null) {
    Column(Modifier.padding(top = 18.dp)) {
        SectionTitle(title)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { MediaCard(it, badge) }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Primary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("See all", color = Accent, fontSize = 12.sp)
    }
}

@Composable
private fun MediaCard(title: String, badge: String?) {
    Column(Modifier.width(154.dp).clickable { }) {
        Box(Modifier.fillMaxWidth().height(210.dp).background(Brush.verticalGradient(listOf(Color(0xFF252C3B), Color(0xFF171A22))), RoundedCornerShape(17.dp))) {
            Text(title.take(1), color = Color.White.copy(alpha = 0.12f), fontSize = 94.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
            if (badge != null) {
                Text(badge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(9.dp).background(if (badge == "LIVE") LiveRed else Accent, RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("AstraWave", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun Pill(label: String, color: Color) {
    Box(Modifier.background(color, RoundedCornerShape(12.dp)).clickable { }.padding(horizontal = 16.dp, vertical = 11.dp)) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
