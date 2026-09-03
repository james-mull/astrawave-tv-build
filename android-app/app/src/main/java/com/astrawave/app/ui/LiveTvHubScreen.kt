package com.astrawave.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.PlayerActivity
import com.astrawave.app.core.IptvSource
import com.astrawave.app.data.CombinedLiveTvRepository
import com.astrawave.app.data.CombinedLiveTvSnapshot
import com.astrawave.app.data.StreamHealthChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LiveTvMode { CHANNELS, SOURCES }

private sealed interface LiveTvLoadState {
    data object Loading : LiveTvLoadState
    data class Ready(val snapshot: CombinedLiveTvSnapshot) : LiveTvLoadState
    data class Error(val message: String) : LiveTvLoadState
}

@Composable
fun LiveTvHubScreen(
    sources: List<IptvSource>,
    onSourcesChanged: (List<IptvSource>) -> Unit,
    repository: CombinedLiveTvRepository = remember { CombinedLiveTvRepository() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(LiveTvMode.CHANNELS) }
    var state by remember(sources) { mutableStateOf<LiveTvLoadState>(LiveTvLoadState.Loading) }

    LaunchedEffect(sources) {
        state = LiveTvLoadState.Loading
        state = try {
            LiveTvLoadState.Ready(withContext(Dispatchers.IO) { repository.load(sources) })
        } catch (error: Exception) {
            LiveTvLoadState.Error(error.message ?: "Unable to load Live TV")
        }
    }

    fun play(url: String) {
        scope.launch {
            val healthy = withContext(Dispatchers.IO) { StreamHealthChecker.check(url).reachable }
            if (!healthy) {
                Toast.makeText(context, "This channel is not reachable right now.", Toast.LENGTH_LONG).show()
                return@launch
            }
            context.startActivity(Intent(context, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_URL, url))
        }
    }

    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiveModeButton("Channels", mode == LiveTvMode.CHANNELS) { mode = LiveTvMode.CHANNELS }
            LiveModeButton("Sources", mode == LiveTvMode.SOURCES) { mode = LiveTvMode.SOURCES }
        }

        when (mode) {
            LiveTvMode.SOURCES -> MyIptvScreen(sources = sources, onSourcesChanged = onSourcesChanged)
            LiveTvMode.CHANNELS -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                AstraWavePageHeader(
                    title = "Live TV",
                    subtitle = "Combined AstraWave Free TV and your enabled IPTV sources.",
                )
                Spacer(Modifier.height(18.dp))
                when (val current = state) {
                    LiveTvLoadState.Loading -> AstraWaveStatePanel("Loading channels…", "Refreshing combined channel inventory and guide data.", loading = true)
                    is LiveTvLoadState.Error -> AstraWaveStatePanel("Live TV unavailable", current.message)
                    is LiveTvLoadState.Ready -> {
                        val snapshot = current.snapshot
                        AstraWaveStatePanel(
                            "${snapshot.totalChannelGroups} channel groups",
                            "${snapshot.freeChannelCount} AstraWave Free TV • ${snapshot.userChannelCount} from My IPTV",
                        )
                        Spacer(Modifier.height(14.dp))
                        if (snapshot.groups.isEmpty()) {
                            AstraWaveStatePanel("No channels yet", "Open Sources to add an M3U or Xtream service. Authorized AstraWave Free TV channels appear here automatically when published.")
                        } else {
                            snapshot.groups.take(300).forEach { group ->
                                val candidate = group.bestCandidate
                                AstraWaveFocusableCard(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .then(candidate?.url?.let { Modifier.clickable { play(it) } } ?: Modifier),
                                ) {
                                    Column {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(Modifier.weight(1f)) {
                                                Text(group.displayName, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                                Text(candidate?.source ?: "Source pending", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelMedium)
                                            }
                                            Text(if (candidate != null) "Play" else "Unavailable", color = if (candidate != null) AstraWaveColors.Success else AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelLarge)
                                        }
                                        group.currentProgram?.title?.let { now ->
                                            Spacer(Modifier.height(5.dp))
                                            Text("Now: $now", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        }
                                        group.nextProgram?.title?.let { next ->
                                            Text("Next: $next", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LiveModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AstraWaveColors.Accent else AstraWaveColors.SurfaceRaised,
            contentColor = AstraWaveColors.PrimaryText,
        ),
    ) {
        Text(label)
    }
}
