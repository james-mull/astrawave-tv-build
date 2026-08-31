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

private val Bg = Color(0xFF090B10)
private val Panel = Color(0xFF151923)
private val PanelAlt = Color(0xFF202532)
private val Primary = Color(0xFFF7F7FA)
private val Muted = Color(0xFFA8AFBD)
private val Accent = Color(0xFF8B5CF6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(color = Bg) { AstraWaveApp() } } }
    }
}

enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home), Movies("Movies", Icons.Default.Movie), Shows("TV Shows", Icons.Default.Tv),
    Live("Live TV", Icons.Default.LiveTv), Guide("Guide", Icons.Default.CalendarMonth), Sports("Sports", Icons.Default.SportsFootball),
    Audio("Music", Icons.Default.MusicNote), Discover("Discover", Icons.Default.Explore), Search("Search", Icons.Default.Search),
    My("My AstraWave", Icons.Default.AccountCircle)
}

@Composable
fun AstraWaveApp() {
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    var current by remember { mutableStateOf(Destination.Home) }
    if (wide) {
        Row(Modifier.fillMaxSize().background(Bg)) {
            NavigationRail(containerColor = Color(0xFF0D1016), modifier = Modifier.width(96.dp)) {
                Spacer(Modifier.height(16.dp))
                Text("AW", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 14.dp))
                Destination.entries.forEach { item ->
                    NavigationRailItem(selected = current == item, onClick = { current = item }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label, fontSize = 9.sp, maxLines = 1) })
                }
            }
            Screen(current, Modifier.weight(1f))
        }
    } else {
        Column(Modifier.fillMaxSize().background(Bg)) {
            Screen(current, Modifier.weight(1f))
            val items = listOf(Destination.Home, Destination.Movies, Destination.Live, Destination.Sports, Destination.My)
            NavigationBar(containerColor = Color(0xFF0D1016)) {
                items.forEach { item -> NavigationBarItem(selected = current == item, onClick = { current = item }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label, maxLines = 1) }) }
            }
        }
    }
}

@Composable
private fun Screen(destination: Destination, modifier: Modifier) {
    Box(modifier.fillMaxSize()) {
        when (destination) {
            Destination.Home -> HomeScreen()
            Destination.Movies -> HubScreen("Movies", listOf("Trending", "New Releases", "Top Rated", "Action", "Comedy", "Thriller", "Sci-Fi", "Family"))
            Destination.Shows -> HubScreen("TV Shows", listOf("Trending", "New Episodes", "Airing Today", "Binge Worthy", "Networks", "Reality", "Documentary", "My Shows"))
            Destination.Live -> HubScreen("Live TV", listOf("Recent", "Favorites", "Local", "News", "Sports", "Entertainment", "Kids", "International"))
            Destination.Guide -> HubScreen("TV Guide", listOf("Now", "Tonight", "Tomorrow", "Sports", "Movies", "Favorites", "Search Guide"))
            Destination.Sports -> HubScreen("Sports", listOf("Live Now", "Starting Soon", "Today", "Tomorrow", "NFL", "NBA", "MLB", "NHL", "Soccer", "Combat Sports"))
            Destination.Audio -> HubScreen("Music & Podcasts", listOf("Recently Played", "Music For You", "Podcasts", "Video Podcasts", "Radio", "Live Performances"))
            Destination.Discover -> HubScreen("Discover", listOf("Watch Tonight", "Surprise Me", "Family Night", "Date Night", "Hidden Gems", "Under 90 Minutes"))
            Destination.Search -> HubScreen("Search", listOf("Movies", "Shows", "Channels", "Sports", "Music", "Podcasts", "People", "Collections"))
            Destination.My -> HubScreen("My AstraWave", listOf("Continue Watching", "Continue Listening", "Watchlist", "Favorite Channels", "Favorite Teams", "Profiles", "Sources", "Settings"))
        }
    }
}

@Composable
private fun HomeScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        Hero()
        MediaRow("Continue Watching", listOf("Night Drive", "Northbound", "Signal", "Afterlight"))
        SportsRow()
        MediaRow("Live Now", listOf("Local News", "Weather Live", "Music Live", "World News"), "LIVE")
        MediaRow("Trending Movies", listOf("Orbit", "The Last Ridge", "Echo City", "Beyond Midnight", "Static"))
        MediaRow("New Episodes", listOf("Frontier S2:E4", "Signal S1:E8", "Deep Water S3:E2", "Afterlight S1:E5"))
        MediaRow("Continue Listening", listOf("Daily Brief", "Road Mix", "Tech Weekly", "True Crime Daily"))
        MediaRow("Watch Tonight", listOf("Family Pick", "Top Movie", "Big Game", "Hidden Gem"))
    }
}

@Composable
private fun Hero() {
    Box(Modifier.fillMaxWidth().height(330.dp).background(Brush.horizontalGradient(listOf(Color(0xFF171C29), Color(0xFF2A183B), Bg))).padding(28.dp)) {
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.8f)) {
            Text("ASTRAWAVE", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("All your entertainment. One place.", color = Primary, fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 38.sp)
            Spacer(Modifier.height(10.dp))
            Text("Movies, TV, live channels, sports, music and podcasts in one clean experience.", color = Muted, fontSize = 15.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Pill("▶ Play Featured", Accent); Pill("＋ My List", PanelAlt) }
        }
    }
}

@Composable
private fun SportsRow() {
    Column(Modifier.padding(top = 24.dp)) {
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
    Column(Modifier.width(260.dp).background(Panel, RoundedCornerShape(18.dp)).clickable { }.padding(18.dp)) {
        Row { Text(league, color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text(time, color = Muted, fontSize = 12.sp) }
        Spacer(Modifier.height(14.dp)); Text(event, color = Primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text("View event", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun HubScreen(title: String, sections: List<String>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        Column(Modifier.padding(24.dp)) { Text(title, color = Primary, fontSize = 30.sp, fontWeight = FontWeight.Black); Text("AstraWave entertainment hub", color = Muted, fontSize = 14.sp) }
        sections.chunked(4).forEachIndexed { index, row -> MediaRow(if (index == 0) "Featured" else "More $title", row) }
    }
}

@Composable
private fun MediaRow(title: String, items: List<String>, badge: String? = null) {
    Column(Modifier.padding(top = 24.dp)) {
        SectionTitle(title)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { MediaCard(it, badge) }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Primary, fontSize = 19.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("See all", color = Accent, fontSize = 12.sp)
    }
}

@Composable
private fun MediaCard(title: String, badge: String?) {
    Column(Modifier.width(150.dp).clickable { }) {
        Box(Modifier.fillMaxWidth().height(205.dp).background(Brush.verticalGradient(listOf(Color(0xFF242B39), Color(0xFF171A22))), RoundedCornerShape(16.dp))) {
            Text(title.take(1), color = Color.White.copy(alpha = 0.12f), fontSize = 92.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
            if (badge != null) Text(badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(9.dp).background(Color(0xFFE84A5F), RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(8.dp)); Text(title, color = Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("AstraWave", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun Pill(label: String, color: Color) {
    Box(Modifier.background(color, RoundedCornerShape(12.dp)).clickable { }.padding(horizontal = 16.dp, vertical = 11.dp)) { Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}
