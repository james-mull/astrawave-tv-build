package com.astrawave.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.LiveProviderType
import com.astrawave.app.data.AstraIntentEngine
import com.astrawave.app.data.DownloadTravelStore
import com.astrawave.app.data.HouseholdWatchStore
import com.astrawave.app.data.IptvSourceStore
import com.astrawave.app.data.LocalDvrGateway
import com.astrawave.app.data.SourceFusionRepository

@Composable
fun AstraWavePremiumPowerCenter(profileId: String = "default") {
    val context = LocalContext.current
    val downloads = remember { DownloadTravelStore(context) }
    val dvr = remember { LocalDvrGateway(context) }
    val household = remember { HouseholdWatchStore(context) }
    val fusion = remember { SourceFusionRepository(context) }
    val iptv = remember(profileId) { IptvSourceStore(context).load(profileId) }
    var travel by remember(profileId) { mutableStateOf(downloads.travelPolicy(profileId)) }
    var astraQuery by remember { mutableStateOf("") }
    var astraResult by remember { mutableStateOf("Ask Astra to help you find something to watch.") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("ASTRAWAVE PREMIUM", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
        AstraWavePageHeader(
            title = "Premium Hub",
            subtitle = "Your premium features, downloads, recordings, household tools and connected services in one place.",
        )

        PremiumMetricRail(
            recordings = dvr.recordings(profileId).size,
            downloads = downloads.downloads(profileId).size,
            providers = iptv.size,
            householdSessions = household.sessions().size,
        )

        PremiumPanel("Astra Search", "Find something to watch using natural language") {
            androidx.compose.material3.OutlinedTextField(
                value = astraQuery,
                onValueChange = { astraQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Try: Find a funny movie under 2 hours") },
            )
            Spacer(Modifier.height(8.dp))
            AstraWavePrimaryButton(
                label = "Ask Astra",
                onClick = {
                    val intent = AstraIntentEngine.parse(astraQuery)
                    astraResult = "${intent.type.name.replace('_', ' ')} • runtime ${intent.maxRuntimeMinutes ?: "any"} min • rating ${intent.minRating ?: "any"}${intent.titleHint?.let { " • like $it" }.orEmpty()}${intent.teamHint?.let { " • team $it" }.orEmpty()}"
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(astraResult, color = AstraWaveColors.SecondaryText)
        }

        PremiumPanel("Playback Reliability", "Automatic backup playback when available") {
            if (iptv.isEmpty()) {
                Text("Connect a Live TV service to see playback reliability here.", color = AstraWaveColors.SecondaryText)
            } else {
                iptv.take(10).forEach { source ->
                    val health = fusion.score(source.id)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(source.name, color = AstraWaveColors.PrimaryText)
                        Text(when { health.score >= 85 -> "Excellent"; health.score >= 70 -> "Good"; else -> "Improving" }, color = if (health.score >= 70) AstraWaveColors.Success else AstraWaveColors.SecondaryText)
                    }
                    Spacer(Modifier.height(7.dp))
                }
            }
        }

        PremiumPanel("Recordings", "Recording features for supported connected TV services") {
            val recordings = dvr.recordings(profileId)
            Text("${recordings.size} scheduled or saved recordings", color = AstraWaveColors.PrimaryText)
            Text("Recording controls appear only when a connected service supports them and AstraWave can use them safely.", color = AstraWaveColors.SecondaryText)
        }

        PremiumPanel("Downloads & Travel Mode", downloads.travelSummary(profileId)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Automatic travel preparation", color = AstraWaveColors.PrimaryText)
                    Text("Wi-Fi only by default; queues only already-authorized direct media.", color = AstraWaveColors.SecondaryText)
                }
                Switch(
                    checked = travel.enabled,
                    onCheckedChange = {
                        travel = travel.copy(enabled = it)
                        downloads.saveTravelPolicy(profileId, travel)
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("Queue: ${downloads.downloads(profileId).count { it.state.name != "COMPLETE" }} pending • ${downloads.downloads(profileId).count { it.state.name == "COMPLETE" }} ready offline", color = AstraWaveColors.SecondaryText)
        }

        PremiumPanel("Watch Night", "Movie-night voting and shared picks") {
            val latest = household.sessions().firstOrNull()
            if (latest == null) {
                Text("No active Watch Night yet. Create one from your household tools when you are ready.", color = AstraWaveColors.SecondaryText)
            } else {
                Text("${latest.name} • ${latest.candidates.size} choices • leader: ${latest.winner()?.title ?: "waiting for votes"}", color = AstraWaveColors.PrimaryText)
            }
        }

        PremiumPanel("Connected Services", "Services AstraWave can work with") {
            val providers = listOf(
                LiveProviderType.M3U,
                LiveProviderType.XTREAM,
                LiveProviderType.STALKER,
                LiveProviderType.JELLYFIN_LIVE_TV,
                LiveProviderType.PLEX_LIVE_TV,
                LiveProviderType.HDHOMERUN,
                LiveProviderType.TVHEADEND,
                LiveProviderType.ENIGMA2,
                LiveProviderType.WEBDAV,
                LiveProviderType.SMB,
                LiveProviderType.LOCAL,
            )
            Text(providers.joinToString(" • ") { it.name.replace('_', ' ') }, color = AstraWaveColors.SecondaryText)
            Spacer(Modifier.height(6.dp))
            Text("Features appear only after a service is connected and the required capability is available.", color = AstraWaveColors.TertiaryText)
        }
    }
}

@Composable
private fun PremiumMetricRail(recordings: Int, downloads: Int, providers: Int, householdSessions: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PremiumMetric("RECORDINGS", recordings.toString(), Modifier.weight(1f))
        PremiumMetric("OFFLINE", downloads.toString(), Modifier.weight(1f))
        PremiumMetric("CONNECTED", providers.toString(), Modifier.weight(1f))
        PremiumMetric("WATCH NIGHTS", householdSessions.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun PremiumMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(AstraWaveColors.SurfaceRaised, MaterialTheme.shapes.large).padding(14.dp)) {
        Text(label, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        Text(value, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun PremiumPanel(title: String, subtitle: String, content: @Composable () -> Unit) {
    AstraWaveFocusableCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(13.dp))
            content()
        }
    }
}
