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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.data.VodPlaybackDiagnosticsStore
import java.text.DateFormat
import java.util.Date

@Composable
fun VodPlaybackDiagnosticsScreen(
    profileId: String = "default",
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { VodPlaybackDiagnosticsStore(context) }
    val decisions = remember(profileId) { store.load(profileId) }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                AstraWavePageHeader(
                    title = "Playback Diagnostics",
                    subtitle = "Recent Play Best decisions for this profile. No stream URLs, credentials, tokens or personal-media locators are stored here.",
                )
            }
            AstraWaveSecondaryButton(label = "Back", onClick = onBack)
        }

        Spacer(Modifier.height(4.dp))
        if (decisions.isEmpty()) {
            AstraWaveStatePanel(
                title = "No Play Best history yet",
                message = "Play a movie or episode with Play Best and AstraWave will record the non-secret source decision here.",
            )
        } else {
            val latest = decisions.first()
            AstraWaveStatePanel(
                title = "Latest • ${latest.provider}",
                message = buildString {
                    append(latest.title)
                    latest.quality?.let { append(" • $it") }
                    latest.latencyMs?.let { append(" • ${it}ms") }
                    append(" • ${latest.backupCount} backup${if (latest.backupCount == 1) "" else "s"}")
                    if (latest.debridOptimized) append(" • Debrid optimized")
                    if (latest.personalMedia) append(" • Owned media")
                },
            )

            Text("RECENT DECISIONS", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
            decisions.take(40).forEach { item ->
                AstraWaveFocusableCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.resolvedAtEpochMs)),
                                color = AstraWaveColors.TertiaryText,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Text(item.provider, color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
                        Text(
                            buildString {
                                item.quality?.let { append(it) }
                                item.latencyMs?.let { if (isNotEmpty()) append(" • "); append("${it}ms") }
                                if (isNotEmpty()) append(" • ")
                                append("${item.providerCount} provider${if (item.providerCount == 1) "" else "s"}")
                                append(" • ${item.backupCount} backup${if (item.backupCount == 1) "" else "s"}")
                            },
                            color = AstraWaveColors.SecondaryText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val flags = buildList {
                            if (item.personalMedia) add("OWNED MEDIA")
                            if (item.debridOptimized) add("DEBRID OPTIMIZED")
                            if (item.backupCount > 0) add("FAILOVER READY")
                        }
                        if (flags.isNotEmpty()) {
                            Text(flags.joinToString("  •  "), color = AstraWaveColors.Success, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
