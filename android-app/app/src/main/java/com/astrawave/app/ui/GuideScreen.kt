package com.astrawave.app.ui

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.PlayerActivity
import com.astrawave.app.core.IptvSource
import com.astrawave.app.core.ProfileSafetyPolicy
import com.astrawave.app.data.GuideRepository
import com.astrawave.app.data.GuideSnapshot
import com.astrawave.app.data.ProfileSafetyStore
import com.astrawave.app.data.StreamHealthChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface GuideLoadState {
    data object Loading : GuideLoadState
    data class Ready(val snapshot: GuideSnapshot) : GuideLoadState
    data class Error(val message: String) : GuideLoadState
}

@Composable
fun AstraWaveGuideScreen(
    sources: List<IptvSource>,
    profileId: String = "default",
    repository: GuideRepository = remember { GuideRepository() },
) {
    val context = LocalContext.current
    val safety = remember(profileId) { ProfileSafetyStore(context).load(profileId) }
    if (!ProfileSafetyPolicy.liveTvAllowed(safety)) {
        Column(Modifier.fillMaxSize().background(AstraWaveColors.Background).padding(24.dp)) {
            AstraWavePageHeader("Guide", "Live TV and guide access are disabled for this kids profile.")
            Spacer(Modifier.height(18.dp))
            AstraWaveEmptyState(
                title = "Restricted by profile settings",
                message = "A household administrator can enable Live TV for this profile from Privacy & Parental Controls.",
            )
        }
        return
    }

    val scope = rememberCoroutineScope()
    var state by remember(sources) { mutableStateOf<GuideLoadState>(GuideLoadState.Loading) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(sources, refreshKey) {
        state = GuideLoadState.Loading
        state = try {
            GuideLoadState.Ready(withContext(Dispatchers.IO) { repository.load(sources) })
        } catch (error: Exception) {
            GuideLoadState.Error(error.message ?: "Unable to load guide")
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Text("ASTRAWAVE LIVE", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(5.dp))
        AstraWavePageHeader(
            title = "Guide",
            subtitle = "A fast merged view across authorized AstraWave Free TV and your own IPTV sources.",
        )
        Spacer(Modifier.height(22.dp))

        when (val current = state) {
            GuideLoadState.Loading -> AstraWaveLoadingState(
                title = "Building your guide",
                message = "Loading channel groups, current programs and available source matches.",
            )

            is GuideLoadState.Error -> AstraWaveErrorState(
                title = "Guide unavailable",
                message = current.message,
                retryLabel = "Refresh guide",
                onRetry = { refreshKey += 1 },
            )

            is GuideLoadState.Ready -> {
                val snapshot = current.snapshot
                GuideSummaryRail(snapshot)
                Spacer(Modifier.height(22.dp))

                if (snapshot.rows.isEmpty()) {
                    AstraWaveEmptyState(
                        title = "No channels yet",
                        message = "Add your own M3U/Xtream source in My IPTV, or use available authorized AstraWave Free TV feeds. The guide will populate when channel data is available.",
                    )
                } else {
                    AstraWaveSectionHeader(
                        title = "Now on TV",
                        subtitle = "Focus a channel to scan what is on now and next. Play is only shown when a healthy eligible stream is available.",
                    )
                    Spacer(Modifier.height(12.dp))

                    snapshot.rows.take(150).forEach { row ->
                        val playableModifier = row.playableUrl?.let { url -> Modifier.clickable { play(url) } } ?: Modifier
                        AstraWaveFocusableCard(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .then(playableModifier),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.width(170.dp)) {
                                    Text(
                                        row.name,
                                        color = AstraWaveColors.PrimaryText,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    GuideSourceBadge(row.preferredSource ?: "Source pending")
                                }

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        row.now?.title ?: "No current program data",
                                        color = AstraWaveColors.PrimaryText,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                    )
                                    row.next?.title?.let { nextTitle ->
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Next  •  $nextTitle",
                                            color = AstraWaveColors.SecondaryText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                        )
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        row.group ?: "Uncategorized",
                                        color = AstraWaveColors.TertiaryText,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (row.playableUrl != null) "READY" else "UNAVAILABLE",
                                        color = if (row.playableUrl != null) AstraWaveColors.Success else AstraWaveColors.TertiaryText,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        if (row.playableUrl != null) "Play channel" else "${row.playableCandidateCount} candidates",
                                        color = AstraWaveColors.SecondaryText,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun GuideSummaryRail(snapshot: GuideSnapshot) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GuideStatPill("CHANNELS", snapshot.sourceGroups.toString())
        GuideStatPill("ASTRAWAVE FREE", snapshot.freeChannelCount.toString())
        GuideStatPill("MY IPTV", snapshot.userChannelCount.toString())
        GuideStatPill("MERGED", (snapshot.freeChannelCount + snapshot.userChannelCount).toString())
    }
}

@Composable
private fun GuideStatPill(label: String, value: String) {
    Column(
        Modifier
            .background(AstraWaveColors.SurfaceRaised, MaterialTheme.shapes.large)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(label, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        Text(value, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun GuideSourceBadge(label: String) {
    Text(
        text = label,
        color = AstraWaveColors.Accent,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(AstraWaveColors.SurfaceRaised, MaterialTheme.shapes.medium)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        maxLines = 1,
    )
}
