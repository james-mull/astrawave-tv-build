package com.astrawave.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.astrawave.app.data.SourceDiscoveryRepository

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

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = DetailsAccent, background = DetailsBg, surface = DetailsPanel)) {
                Surface(color = DetailsBg) {
                    TitleDetailsScreen(
                        title = title,
                        year = year,
                        onBack = { finish() },
                        onPlay = { url ->
                            startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_URL, url))
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_YEAR = "year"
    }
}

@Composable
private fun TitleDetailsScreen(
    title: String,
    year: Int?,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
) {
    val repository = remember { SourceDiscoveryRepository() }
    var refreshToken by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var sources by remember { mutableStateOf<List<ResolvedSource>>(emptyList()) }

    LaunchedEffect(title, year, refreshToken) {
        loading = true
        error = null
        sources = runCatching {
            repository.discover(ScrapeRequest(title = title, year = year))
        }.onFailure {
            error = it.message ?: "Source discovery failed"
        }.getOrDefault(emptyList())
        loading = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(title, color = DetailsPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(year?.toString() ?: "Source discovery", color = DetailsMuted, fontSize = 13.sp)
            }
            IconButton(onClick = { refreshToken++ }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
        }

        Text(
            "AstraWave checks every enabled approved provider, removes duplicates, tests stream health, and ranks the best playable source first.",
            color = DetailsMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        when {
            loading -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text("Finding playable sources…", color = DetailsPrimary)
                }
            }
            error != null -> {
                Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
            }
            sources.isEmpty() -> {
                Column(
                    Modifier.fillMaxWidth().background(DetailsPanel, RoundedCornerShape(18.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No verified source found", color = DetailsPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("AstraWave did not find a healthy approved stream for this title. Add more authorized providers or try again later.", color = DetailsMuted, fontSize = 13.sp)
                }
            }
            else -> {
                Text("Available Sources", color = DetailsPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                sources.forEachIndexed { index, source ->
                    SourceCard(index = index, source = source, onPlay = onPlay)
                }
            }
        }
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
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(42.dp).background(if (index == 0) DetailsAccent else Color(0xFF202736), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(source.link.sourceName, color = DetailsPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (index == 0) {
                    Text("BEST", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.background(DetailsAccent, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
            val details = listOfNotNull(
                source.link.quality,
                source.contentType,
                source.latencyMs?.let { "${it}ms" },
                source.link.licenseLabel
            ).joinToString(" • ")
            Text(details.ifBlank { "Verified stream" }, color = DetailsMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Health verified", color = DetailsSuccess, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
