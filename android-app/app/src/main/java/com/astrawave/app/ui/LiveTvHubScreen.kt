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
import com.astrawave.app.core.MultiviewPane
import com.astrawave.app.core.ProfileSafetyPolicy
import com.astrawave.app.data.CombinedLiveTvRepository
import com.astrawave.app.data.CombinedLiveTvSnapshot
import com.astrawave.app.data.LiveChannelGroup
import com.astrawave.app.data.ProfileSafetyStore
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
    multiviewCount: Int = 0,
    onAddToMultiview: (MultiviewPane) -> Unit = {},
    onOpenMultiview: () -> Unit = {},
    profileId: String = "default",
    repository: CombinedLiveTvRepository = remember { CombinedLiveTvRepository() },
) {
    val context = LocalContext.current
    val safety = remember(profileId) { ProfileSafetyStore(context).load(profileId) }
    if (!ProfileSafetyPolicy.liveTvAllowed(safety)) {
        Column(Modifier.fillMaxSize().background(AstraWaveColors.Background).padding(24.dp)) {
            AstraWavePageHeader("Live TV", "Live TV is disabled for this kids profile.")
            Spacer(Modifier.height(18.dp))
            AstraWaveStatePanel(
                "Restricted by profile settings",
                "A household administrator can enable Live TV for this profile from Privacy & Parental Controls.",
            )
        }
        return
    }

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

    fun play(group: LiveChannelGroup) {
        scope.launch {
            val healthyCandidates = withContext(Dispatchers.IO) {
                group.candidates.filter { candidate ->
                    runCatching { StreamHealthChecker.check(candidate.url).reachable }.getOrDefault(false)
                }
            }
            if (healthyCandidates.isEmpty()) {
                Toast.makeText(context, "No working stream is available for this channel right now.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val urls = ArrayList(healthyCandidates.map { it.url }.distinct())
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URL, urls.first())
                    .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, urls)
                    .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true),
            )
        }
    }

    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
            AstraWavePageHeader(
                title = "Live TV",
                subtitle = "AstraWave Free TV, IPTV.org Public, and your own IPTV are merged with health-checked stream failover.",
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LiveModeButton("Watch", mode == LiveTvMode.CHANNELS) { mode = LiveTvMode.CHANNELS }
                LiveModeButton("My Sources", mode == LiveTvMode.SOURCES) { mode = LiveTvMode.SOURCES }
                if (multiviewCount > 0) {
                    LiveModeButton("Multiview ($multiviewCount)", false, onOpenMultiview)
                }
            }
        }

        when (mode) {
            LiveTvMode.SOURCES -> MyIptvScreen(sources = sources, onSourcesChanged = onSourcesChanged)
            LiveTvMode.CHANNELS -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 4.dp),
            ) {
                when (val current = state) {
                    LiveTvLoadState.Loading -> AstraWaveStatePanel(
                        "Getting Live TV ready…",
                        "Loading AstraWave Free TV, IPTV.org Public, and your enabled sources.",
                        loading = true,
                    )
                    is LiveTvLoadState.Error -> AstraWaveStatePanel("Live TV unavailable", current.message)
                    is LiveTvLoadState.Ready -> {
                        val snapshot = current.snapshot
                        val totalChannels = snapshot.freeChannelCount + snapshot.userChannelCount
                        AstraWaveStatePanel(
                            "$totalChannels channel candidates loaded",
                            when {
                                snapshot.userChannelCount > 0 -> "${snapshot.freeChannelCount} included free • ${snapshot.userChannelCount} from your sources • verified when selected"
                                snapshot.freeChannelCount > 0 -> "${snapshot.freeChannelCount} included free candidates • AstraWave tests all alternates before playback"
                                else -> "No free channel candidates are available right now. You can still add your own IPTV source."
                            },
                        )
                        Spacer(Modifier.height(18.dp))

                        if (snapshot.groups.isEmpty()) {
                            AstraWaveStatePanel(
                                "No channels available",
                                "Try again later, or open My Sources to add an M3U or Xtream service.",
                            )
                        } else {
                            snapshot.groups.take(500).forEach { group ->
                                val candidate = group.bestCandidate
                                AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                    Column {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    group.displayName,
                                                    color = AstraWaveColors.PrimaryText,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 2,
                                                )
                                                Spacer(Modifier.height(3.dp))
                                                Text(
                                                    candidate?.source ?: "Source unavailable",
                                                    color = AstraWaveColors.Accent,
                                                    style = MaterialTheme.typography.labelMedium,
                                                )
                                            }
                                            if (candidate != null) {
                                                val multiviewEligible = PlayerActivity.isDirectMediaUrl(candidate.url)
                                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                                    Text(
                                                        "Check & Watch",
                                                        color = AstraWaveColors.Success,
                                                        modifier = Modifier.clickable { play(group) },
                                                        style = MaterialTheme.typography.labelLarge,
                                                    )
                                                    if (multiviewEligible) {
                                                        Text(
                                                            if (multiviewCount >= 4) "Multiview full" else "+ Multiview",
                                                            color = if (multiviewCount >= 4) AstraWaveColors.TertiaryText else AstraWaveColors.PrimaryText,
                                                            modifier = Modifier.clickable(enabled = multiviewCount < 4) {
                                                                onAddToMultiview(
                                                                    MultiviewPane(
                                                                        id = "live:${group.canonicalName}",
                                                                        title = group.displayName,
                                                                        streamUrl = candidate.url,
                                                                        sourceName = candidate.source,
                                                                        channelId = candidate.id,
                                                                    ),
                                                                )
                                                            },
                                                            style = MaterialTheme.typography.labelLarge,
                                                        )
                                                    }
                                                }
                                            } else {
                                                Text("Unavailable", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                        Text(
                                            "${group.candidates.size} stream candidate${if (group.candidates.size == 1) "" else "s"}",
                                            color = AstraWaveColors.TertiaryText,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                        group.currentProgram?.title?.let { now ->
                                            Spacer(Modifier.height(8.dp))
                                            Text("Now • $now", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        }
                                        group.nextProgram?.title?.let { next ->
                                            Text("Next • $next", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
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
        shape = MaterialTheme.shapes.large,
    ) {
        Text(label)
    }
}
